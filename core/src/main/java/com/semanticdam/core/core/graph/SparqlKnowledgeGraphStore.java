package com.semanticdam.core.core.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.ontology.Vocabulary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Stores the graph in an external triplestore over the SPARQL 1.1 Protocol.
 *
 * <p>Each statement is written twice: as a plain triple, so that graph tools and federated queries
 * see ordinary RDF, and as an {@code rdf:Statement} carrying the provenance (source, confidence,
 * validity) and the DAM path of its asset. Attribution uses W3C PROV-O ({@code prov:wasAttributedTo}
 * an agent IRI). Reads use the reified form, so they round-trip exactly
 * what was written. Entities are {@code <iri> a <type> ; skos:prefLabel "label"}.</p>
 */
public class SparqlKnowledgeGraphStore implements KnowledgeGraphService {
    private static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String XSD = "http://www.w3.org/2001/XMLSchema#";
    static final String ASSET_PATH = Vocabulary.SDAM + "assetPath";
    static final String ATTRIBUTED_TO = Vocabulary.PROV + "wasAttributedTo";
    static final String CONFIDENCE = Vocabulary.SDAM + "confidence";
    static final String VALID = Vocabulary.SDAM + "valid";
    static final String OBJECT_IS_URI = Vocabulary.SDAM + "objectIsUri";
    static final String POSITION = Vocabulary.SDAM + "position";
    private static final String BROADER = Vocabulary.SKOS + "broader";
    private static final String ALT_LABEL = Vocabulary.SKOS + "altLabel";
    private static final String HIDDEN_LABEL = Vocabulary.SKOS + "hiddenLabel";
    private static final String IN_SCHEME = Vocabulary.SKOS + "inScheme";
    private static final String STATUS = Vocabulary.SDAM + "vocabularyStatus";
    private static final String REPLACED_BY = Vocabulary.DCTERMS + "isReplacedBy";

    private final SparqlClient client;
    private final GraphSubjectResolver subjects;
    private final String graphUri;

    public SparqlKnowledgeGraphStore(SparqlClient client, GraphSubjectResolver subjects, String graphUri) {
        if (client == null || subjects == null) {
            throw new IllegalArgumentException("client and subject resolver must be provided");
        }
        this.client = client;
        this.subjects = subjects;
        this.graphUri = graphUri == null || graphUri.isBlank() ? null : graphUri;
    }

    @Override
    public void writeAssetMetadata(AssetMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must be provided");
        }
        String subject = iri(subjects.assetSubject(metadata.assetId()));
        String path = literal(metadata.assetId().path());
        StringBuilder data = new StringBuilder();
        int position = 0;
        for (MetadataStatement statement : metadata.statements()) {
            String object = statement.objectIsUri() ? iri(statement.objectValue()) : literal(statement.objectValue());
            data.append(subject).append(' ').append(iri(statement.predicateUri())).append(' ').append(object).append(" .\n");
            data.append("[] a <").append(RDF).append("Statement> ; <").append(RDF).append("subject> ").append(subject)
                    .append(" ; <").append(RDF).append("predicate> ").append(iri(statement.predicateUri()))
                    .append(" ; <").append(RDF).append("object> ").append(object)
                    .append(" ; <").append(ASSET_PATH).append("> ").append(path)
                    .append(" ; <").append(ATTRIBUTED_TO).append("> ").append(iri(Vocabulary.agentIri(statement.source())))
                    .append(" ; <").append(CONFIDENCE).append("> \"").append(statement.confidenceScore())
                    .append("\"^^<").append(XSD).append("double>")
                    .append(" ; <").append(VALID).append("> ").append(statement.isValid())
                    .append(" ; <").append(OBJECT_IS_URI).append("> ").append(statement.objectIsUri())
                    .append(" ; <").append(POSITION).append("> ").append(++position).append(" .\n");
        }
        // Delete and insert in one request so that readers never see a half-written asset.
        String update = deleteAsset(subject, path)
                + (data.length() == 0 ? "" : " ;\nINSERT DATA { " + inGraph(data.toString()) + " }");
        execute(() -> client.update(update), "write " + metadata.assetId().path());
    }

    @Override
    public void deleteAsset(AssetId assetId) {
        if (assetId == null) {
            return;
        }
        String update = deleteAsset(iri(subjects.assetSubject(assetId)), literal(assetId.path()));
        execute(() -> client.update(update), "delete " + assetId.path());
    }

    private String deleteAsset(String subject, String path) {
        // Two DELETE WHERE operations: the asset's plain triples, then its provenance nodes.
        return "DELETE WHERE { " + inGraph(subject + " ?p ?o .") + " } ;\n"
                + "DELETE WHERE { " + inGraph("?st <" + ASSET_PATH + "> " + path + " ; ?sp ?so .") + " }";
    }

    @Override
    public Optional<AssetMetadata> findByAssetId(AssetId assetId) {
        if (assetId == null) {
            return Optional.empty();
        }
        List<Map<String, JsonObject>> rows = select("SELECT ?p ?o ?agent ?confidence ?valid ?uri WHERE { "
                + inGraph("?st <" + ASSET_PATH + "> " + literal(assetId.path()) + " ; <" + RDF + "predicate> ?p ; <"
                        + RDF + "object> ?o ; <" + ATTRIBUTED_TO + "> ?agent ; <" + CONFIDENCE + "> ?confidence ; <"
                        + VALID + "> ?valid ; <" + OBJECT_IS_URI + "> ?uri ; <" + POSITION + "> ?position .")
                + " } ORDER BY ?position", "read " + assetId.path());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<MetadataStatement> statements = new ArrayList<>();
        for (Map<String, JsonObject> row : rows) {
            String predicate = value(row, "p");
            String object = value(row, "o");
            String source = Vocabulary.sourceLabel(value(row, "agent"));
            boolean valid = Boolean.parseBoolean(value(row, "valid"));
            double confidence = Double.parseDouble(value(row, "confidence"));
            statements.add(Boolean.parseBoolean(value(row, "uri"))
                    ? MetadataStatement.uri(predicate, object, source, valid, confidence)
                    : MetadataStatement.literal(predicate, object, source, valid, confidence));
        }
        return Optional.of(new AssetMetadata(assetId, statements));
    }

    @Override
    public Collection<AssetMetadata> findByPredicateValue(String predicateUri, String objectValue) {
        if (predicateUri == null || predicateUri.isBlank() || objectValue == null || objectValue.isBlank()) {
            return List.of();
        }
        return assetsMatching("?st <" + RDF + "predicate> " + iri(predicateUri) + " ; <" + RDF + "object> ?o ; <"
                + ASSET_PATH + "> ?path . FILTER(STR(?o) = " + literal(objectValue) + ")");
    }

    @Override
    public Collection<AssetMetadata> findReferencing(String entityUri) {
        if (entityUri == null || entityUri.isBlank()) {
            return List.of();
        }
        return assetsMatching("?st <" + RDF + "object> " + iri(entityUri) + " ; <" + RDF + "predicate> ?p ; <"
                + ASSET_PATH + "> ?path . FILTER(?p != <" + Vocabulary.RDF_TYPE + ">)");
    }

    private Collection<AssetMetadata> assetsMatching(String pattern) {
        Set<String> paths = new LinkedHashSet<>();
        for (Map<String, JsonObject> row : select("SELECT DISTINCT ?path WHERE { " + inGraph(pattern)
                + " } ORDER BY ?path", "query")) {
            paths.add(value(row, "path"));
        }
        List<AssetMetadata> result = new ArrayList<>();
        for (String path : paths) {
            findByAssetId(AssetId.of(path)).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public void writeEntity(Entity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must be provided");
        }
        String node = iri(entity.uri());
        StringBuilder data = new StringBuilder(node).append(" a ").append(iri(entity.typeUri()))
                .append(" ; <").append(Vocabulary.PREF_LABEL).append("> ").append(literal(entity.label()))
                .append(" ; <").append(STATUS).append("> ").append(literal(entity.status().name()));
        entity.broader().forEach(parent -> data.append(" ; <").append(BROADER).append("> ").append(iri(parent)));
        entity.altLabels().forEach(label -> data.append(" ; <").append(ALT_LABEL).append("> ").append(literal(label)));
        entity.hiddenLabels().forEach(label -> data.append(" ; <").append(HIDDEN_LABEL).append("> ").append(literal(label)));
        if (entity.scheme() != null) {
            data.append(" ; <").append(IN_SCHEME).append("> ").append(iri(entity.scheme()));
        }
        if (entity.replacedBy() != null) {
            data.append(" ; <").append(REPLACED_BY).append("> ").append(iri(entity.replacedBy()));
        }
        String update = "DELETE WHERE { " + inGraph(node + " ?p ?o .") + " } ;\n"
                + "INSERT DATA { " + inGraph(data + " .") + " }";
        execute(() -> client.update(update), "write entity " + entity.uri());
    }

    @Override
    public Optional<Entity> findEntity(String entityUri) {
        if (entityUri == null || entityUri.isBlank()) {
            return Optional.empty();
        }
        String node = iri(entityUri);
        List<Map<String, JsonObject>> rows = select("SELECT ?p ?o WHERE { " + inGraph(node + " ?p ?o .") + " }",
                "read entity " + entityUri);
        String type = null;
        String label = null;
        String scheme = null;
        String replacedBy = null;
        Entity.Status status = Entity.Status.APPROVED;
        List<String> broader = new ArrayList<>();
        List<String> alt = new ArrayList<>();
        List<String> hidden = new ArrayList<>();
        for (Map<String, JsonObject> row : rows) {
            String predicate = value(row, "p");
            String object = value(row, "o");
            switch (predicate) {
                case Vocabulary.RDF_TYPE -> type = object;
                case Vocabulary.PREF_LABEL -> label = object;
                case STATUS -> status = Entity.Status.valueOf(object);
                case BROADER -> broader.add(object);
                case ALT_LABEL -> alt.add(object);
                case HIDDEN_LABEL -> hidden.add(object);
                case IN_SCHEME -> scheme = object;
                case REPLACED_BY -> replacedBy = object;
                default -> { }
            }
        }
        if (type == null || label == null) {
            return Optional.empty();
        }
        java.util.Collections.sort(broader);
        java.util.Collections.sort(alt);
        java.util.Collections.sort(hidden);
        return Optional.of(new Entity(entityUri, type, label, broader, alt, hidden, scheme, status, replacedBy));
    }

    @Override
    public Collection<Entity> findEntities(Entity.Status status, String typeUri) {
        List<Entity> result = new ArrayList<>();
        for (Map<String, JsonObject> row : select("SELECT DISTINCT ?e WHERE { " + inGraph("?e <" + STATUS + "> "
                + literal(status.name()) + (typeUri == null ? "" : " ; a " + iri(typeUri)) + " .") + " } ORDER BY ?e",
                "list entities")) {
            findEntity(value(row, "e")).ifPresent(result::add);
        }
        return result;
    }

    private List<Map<String, JsonObject>> select(String query, String action) {
        String body;
        try {
            body = client.query(query).orElseThrow(() -> new IllegalStateException("empty SPARQL response"));
        } catch (RuntimeException e) {
            throw new GraphStoreException("SPARQL " + action + " failed", e);
        }
        try {
            JsonArray bindings = JsonParser.parseString(body).getAsJsonObject()
                    .getAsJsonObject("results").getAsJsonArray("bindings");
            List<Map<String, JsonObject>> rows = new ArrayList<>();
            for (JsonElement binding : bindings) {
                Map<String, JsonObject> row = new java.util.HashMap<>();
                for (Map.Entry<String, JsonElement> entry : binding.getAsJsonObject().entrySet()) {
                    row.put(entry.getKey(), entry.getValue().getAsJsonObject());
                }
                rows.add(row);
            }
            return rows;
        } catch (RuntimeException e) {
            throw new GraphStoreException("SPARQL " + action + " returned a malformed result", e);
        }
    }

    private static String value(Map<String, JsonObject> row, String name) {
        JsonObject binding = row.get(name);
        if (binding == null) {
            throw new GraphStoreException("SPARQL result is missing ?" + name, null);
        }
        return binding.get("value").getAsString();
    }

    private void execute(Runnable action, String description) {
        try {
            action.run();
        } catch (RuntimeException e) {
            throw new GraphStoreException("SPARQL " + description + " failed", e);
        }
    }

    private String inGraph(String pattern) {
        return graphUri == null ? pattern : "GRAPH " + iri(graphUri) + " { " + pattern + " }";
    }

    private static String iri(String value) {
        return SimpleTurtleSerializer.iri(value);
    }

    private static String literal(String value) {
        return SimpleTurtleSerializer.literal(value);
    }
}
