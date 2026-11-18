package com.semanticdam.core.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.domain.OntologyTerm;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import com.semanticdam.core.core.graph.OntologyRegistry;
import com.semanticdam.core.core.ontology.NodeShape;
import com.semanticdam.core.core.ontology.PropertyMapping;
import com.semanticdam.core.core.ontology.Violation;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Assembles what an agent or application needs to know about an asset into one document: its
 * authoritative facts with provenance, the entities it links to, the assets it is related to and
 * why, what is wrong with it, and what it is ready for.
 *
 * <p>Every method takes a {@code canRead} predicate. The graph is read with a service user, so
 * this predicate is the only thing keeping a caller from learning about assets they may not see;
 * servlets pass a check against the caller's own session.</p>
 */
@Component(service = AssetContextService.class)
public class AssetContextService {
    private final KnowledgeGraphService graph;
    private final OntologyRegistry ontology;
    private final ConflictResolutionService conflicts;
    private final MetadataValidator validator;
    private final GraphTraversalService traversal;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    private volatile SemanticSearchService semanticSearch;

    @Activate
    public AssetContextService(@Reference KnowledgeGraphService graph, @Reference OntologyRegistry ontology,
            @Reference ConflictResolutionService conflicts, @Reference MetadataValidator validator,
            @Reference GraphTraversalService traversal) {
        this.graph = graph;
        this.ontology = ontology;
        this.conflicts = conflicts;
        this.validator = validator;
        this.traversal = traversal;
    }

    /** @return the context document, or empty if the asset is not in the graph or not readable */
    public Optional<JsonObject> describe(AssetId assetId, int relatedLimit, Predicate<String> canRead) {
        if (!canRead.test(assetId.path())) {
            return Optional.empty();
        }
        Optional<AssetMetadata> stored = graph.findByAssetId(assetId);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        AssetMetadata metadata = stored.get();
        Function<String, Optional<Entity>> entities = entityLookup();
        Collection<MetadataStatement> authoritative = conflicts.resolve(metadata);

        JsonObject document = new JsonObject();
        document.addProperty("path", assetId.path());
        document.addProperty("ontologyVersion", ontology.version());

        JsonArray types = new JsonArray();
        types.add(Vocabulary.ASSET);
        JsonArray facts = new JsonArray();
        Map<String, Entity> linked = new LinkedHashMap<>();
        for (MetadataStatement statement : metadata.statements()) {
            if (Vocabulary.RDF_TYPE.equals(statement.predicateUri())) {
                types.add(statement.objectValue());
                continue;
            }
            JsonObject fact = fact(statement, entities);
            fact.addProperty("authoritative", authoritative.contains(statement));
            facts.add(fact);
            if (statement.objectIsUri()) {
                entities.apply(statement.objectValue()).ifPresent(entity -> linked.putIfAbsent(entity.uri(), entity));
            }
        }
        document.add("types", types);
        document.add("facts", facts);

        JsonArray entityArray = new JsonArray();
        linked.values().forEach(entity -> entityArray.add(entity(entity)));
        document.add("entities", entityArray);

        JsonArray violations = new JsonArray();
        for (Violation violation : validator.violations(authoritative, entities)) {
            JsonObject item = new JsonObject();
            item.addProperty("predicate", violation.path());
            item.addProperty("label", label(violation.path()));
            if (violation.value() != null) {
                item.addProperty("value", violation.value());
            }
            item.addProperty("constraint", violation.constraint());
            item.addProperty("message", violation.message());
            violations.add(item);
        }
        document.add("violations", violations);

        JsonArray readyFor = new JsonArray();
        List<MetadataStatement> valid = metadata.statements().stream().filter(MetadataStatement::isValid).toList();
        for (NodeShape shape : ontology.readinessShapes()) {
            if (validator.conforms(shape, valid, entities)) {
                readyFor.add(shape.label());
            }
        }
        document.add("readyFor", readyFor);
        document.add("related", related(assetId, relatedLimit, canRead, entities));
        return Optional.of(document);
    }

    public JsonArray related(AssetId assetId, int limit, Predicate<String> canRead) {
        return related(assetId, limit, canRead, entityLookup());
    }

    private JsonArray related(AssetId assetId, int limit, Predicate<String> canRead,
            Function<String, Optional<Entity>> entities) {
        JsonArray related = new JsonArray();
        if (!canRead.test(assetId.path())) {
            return related;
        }
        // Over-fetch so that filtering unreadable assets does not shrink the page unnecessarily.
        for (GraphTraversalService.RelatedAsset candidate : traversal.related(assetId, Math.max(limit, 0) * 4)) {
            if (related.size() >= limit) {
                break;
            }
            if (!canRead.test(candidate.asset().assetId().path())) {
                continue;
            }
            JsonObject item = summary(candidate.asset(), entities);
            item.addProperty("score", Math.round(candidate.score() * 1000) / 1000.0);
            JsonArray via = new JsonArray();
            for (GraphTraversalService.Link link : candidate.sharedLinks()) {
                JsonObject reason = new JsonObject();
                reason.addProperty("predicate", link.predicateUri());
                reason.addProperty("label", label(link.predicateUri()));
                reason.addProperty("entity", link.entityUri());
                entities.apply(link.entityUri()).ifPresent(entity -> reason.addProperty("entityLabel", entity.label()));
                via.add(reason);
            }
            item.add("via", via);
            related.add(item);
        }
        return related;
    }

    /**
     * Finds assets by a predicate (IRI or label, e.g. "Brand") and a value. For entity-valued
     * predicates the value may be the entity's IRI or its label (e.g. "Acme").
     *
     * @throws IllegalArgumentException if the predicate is not a property of the ontology
     */
    public JsonArray find(String predicate, String value, int limit, Predicate<String> canRead) {
        JsonArray results = new JsonArray();
        String[] fact = resolveFact(predicate, value);
        String predicateUri = fact[0];
        String object = fact[1];
        Function<String, Optional<Entity>> entities = entityLookup();
        // A search on a predicate also covers the predicates that expand it, so "Subject = EMEA"
        // finds assets indexed at Italy (broaderSubject EMEA) without indexing them at EMEA.
        Map<AssetId, AssetMetadata> found = new java.util.LinkedHashMap<>();
        for (AssetMetadata match : graph.findByPredicateValue(predicateUri, object)) {
            found.putIfAbsent(match.assetId(), match);
        }
        for (String expansion : ontology.expansionsOf(predicateUri)) {
            for (AssetMetadata match : graph.findByPredicateValue(expansion, object)) {
                found.putIfAbsent(match.assetId(), match);
            }
        }
        List<AssetMetadata> matches = new java.util.ArrayList<>(found.values());
        matches.sort(java.util.Comparator.comparing(match -> match.assetId().path()));
        for (AssetMetadata match : matches) {
            if (results.size() >= limit) {
                break;
            }
            if (canRead.test(match.assetId().path())) {
                results.add(summary(match, entities));
            }
        }
        return results;
    }

    /** Whether semantic search is configured (an embedding provider and a vector index). */
    public boolean semanticSearchAvailable() {
        return semanticSearch != null;
    }

    void setSemanticSearch(SemanticSearchService semanticSearch) {
        this.semanticSearch = semanticSearch;
    }

    /**
     * Assets closest in meaning to a natural-language query. With a predicate and value, assets
     * that also have that fact are boosted (and included even if the vector search missed them).
     *
     * @throws IllegalStateException if semantic search is not configured
     */
    public JsonArray semanticSearch(String query, String predicate, String value, int limit, Predicate<String> canRead) {
        SemanticSearchService search = semanticSearch;
        if (search == null) {
            throw new IllegalStateException("Semantic search is not configured: an embedding provider and a "
                    + "vector index are both required");
        }
        // Over-fetch so that filtering out unreadable assets still fills the page.
        int fetch = Math.min(200, Math.max(limit, 1) * 4);
        List<Map.Entry<AssetId, double[]>> scored = new java.util.ArrayList<>();
        if (predicate != null && !predicate.isBlank() && value != null && !value.isBlank()) {
            String[] fact = resolveFact(predicate, value);
            for (HybridRetrievalResult result : search.hybrid(query, fact[0], fact[1], fetch)) {
                scored.add(Map.entry(result.assetId(), new double[] {result.score(), result.graphMatch() ? 1 : 0}));
            }
        } else {
            for (VectorMatch match : search.search(query, fetch)) {
                scored.add(Map.entry(match.assetId(), new double[] {match.score(), -1}));
            }
        }
        Function<String, Optional<Entity>> entities = entityLookup();
        JsonArray results = new JsonArray();
        for (Map.Entry<AssetId, double[]> entry : scored) {
            if (results.size() >= limit) {
                break;
            }
            if (!canRead.test(entry.getKey().path())) {
                continue;
            }
            Optional<AssetMetadata> metadata = graph.findByAssetId(entry.getKey());
            if (metadata.isEmpty()) {
                continue;
            }
            JsonObject item = summary(metadata.get(), entities);
            item.addProperty("score", entry.getValue()[0]);
            if (entry.getValue()[1] >= 0) {
                item.addProperty("matchesFilter", entry.getValue()[1] > 0);
            }
            results.add(item);
        }
        return results;
    }

    /** Resolves a predicate label or IRI and an entity label to IRIs. Only ontology properties are accepted. */
    private String[] resolveFact(String predicate, String value) {
        Optional<OntologyTerm> term = ontology.findByUri(predicate)
                .or(() -> ontology.terms().stream()
                        .filter(OntologyTerm::isProperty)
                        .filter(candidate -> candidate.label().equalsIgnoreCase(predicate.trim()))
                        .findFirst());
        if (term.isEmpty() || !term.get().isProperty()) {
            // Only predicates declared in the ontology are queryable; this is not a generic repository search.
            throw new IllegalArgumentException("Unknown predicate '" + predicate
                    + "'. Use a property IRI or label from the ontology.");
        }
        String object = value;
        if (term.flatMap(OntologyTerm::range).isPresent() && !value.contains("://")) {
            String classUri = term.get().range().get();
            object = ontology.findEntityByLabel(classUri, value).map(Entity::uri)
                    .orElseGet(() -> ontology.instanceNamespace(classUri).orElse("")
                            + AemAssetMetadataMapper.slug(value));
        }
        return new String[] {term.get().uri(), object};
    }

    /** Explains how an asset is connected to another asset or to an entity. */
    public Optional<JsonArray> explainPath(AssetId from, String target, int maxHops, Predicate<String> canRead) {
        if (!canRead.test(from.path())) {
            return Optional.empty();
        }
        Function<String, Optional<Entity>> entities = entityLookup();
        return traversal.shortestPath(from, target, maxHops)
                .filter(path -> path.stream().allMatch(step -> !step.asset() || canRead.test(step.node())))
                .map(path -> {
                    JsonArray steps = new JsonArray();
                    for (GraphTraversalService.PathStep step : path) {
                        JsonObject item = new JsonObject();
                        item.addProperty("kind", step.asset() ? "asset" : "entity");
                        item.addProperty("id", step.node());
                        if (!step.asset()) {
                            entities.apply(step.node()).ifPresent(entity -> {
                                item.addProperty("label", entity.label());
                                item.addProperty("type", entity.typeUri());
                            });
                        }
                        if (step.predicateUri() != null) {
                            item.addProperty("via", step.predicateUri());
                            item.addProperty("viaLabel", label(step.predicateUri()));
                        }
                        steps.add(item);
                    }
                    return steps;
                });
    }

    /** The vocabulary an agent may use: classes, properties, source mappings and shapes. */
    public JsonObject describeOntology() {
        JsonObject document = new JsonObject();
        document.addProperty("version", ontology.version());
        JsonArray classes = new JsonArray();
        JsonArray properties = new JsonArray();
        JsonArray concepts = new JsonArray();
        for (OntologyTerm term : ontology.terms()) {
            JsonObject item = new JsonObject();
            item.addProperty("uri", term.uri());
            item.addProperty("label", term.label());
            term.comment().ifPresent(definition -> item.addProperty("definition", definition));
            switch (term.kind()) {
                case CLASS -> classes.add(item);
                case CONCEPT -> {
                    JsonArray broader = new JsonArray();
                    ontology.broaderConcepts(term.uri()).forEach(broader::add);
                    item.add("broader", broader);
                    concepts.add(item);
                }
                case DATATYPE_PROPERTY, OBJECT_PROPERTY -> {
                    item.addProperty("valueKind", term.kind() == OntologyTerm.Kind.OBJECT_PROPERTY ? "entity" : "literal");
                    term.range().ifPresent(range -> item.addProperty("range", range));
                    term.validationPattern().ifPresent(pattern -> item.addProperty("pattern", pattern));
                    properties.add(item);
                }
                default -> { }
            }
        }
        document.add("classes", classes);
        document.add("properties", properties);
        document.add("concepts", concepts);
        JsonArray mappings = new JsonArray();
        for (PropertyMapping mapping : ontology.mappings()) {
            JsonObject item = new JsonObject();
            item.addProperty("damProperty", mapping.sourceProperty());
            item.addProperty("source", mapping.sourceSystem().label());
            item.addProperty("trust", mapping.sourceSystem().trust());
            item.addProperty("predicate", mapping.predicateUri());
            mapping.entityClass().ifPresent(entityClass -> {
                item.addProperty("entityClass", entityClass);
                item.addProperty("newEntries", mapping.isAemTag() || mapping.createsApprovedEntities()
                        ? "approved (source is the authority)" : "candidate (steward review)");
            });
            mappings.add(item);
        }
        document.add("mappings", mappings);
        JsonArray readiness = new JsonArray();
        ontology.readinessShapes().forEach(shape -> readiness.add(shape.label()));
        document.add("readinessTargets", readiness);
        return document;
    }

    private JsonObject summary(AssetMetadata metadata, Function<String, Optional<Entity>> entities) {
        JsonObject item = new JsonObject();
        item.addProperty("path", metadata.assetId().path());
        JsonArray facts = new JsonArray();
        for (MetadataStatement statement : conflicts.resolve(metadata)) {
            if (statement.isValid() && !Vocabulary.RDF_TYPE.equals(statement.predicateUri())) {
                facts.add(fact(statement, entities));
            }
        }
        item.add("facts", facts);
        return item;
    }

    private JsonObject fact(MetadataStatement statement, Function<String, Optional<Entity>> entities) {
        JsonObject fact = new JsonObject();
        fact.addProperty("predicate", statement.predicateUri());
        fact.addProperty("label", label(statement.predicateUri()));
        fact.addProperty("value", statement.objectValue());
        fact.addProperty("valueIsEntity", statement.objectIsUri());
        if (statement.objectIsUri()) {
            entities.apply(statement.objectValue())
                    .ifPresent(entity -> fact.addProperty("valueLabel", entity.label()));
        }
        fact.addProperty("source", statement.source());
        fact.addProperty("sourceAgent", Vocabulary.agentIri(statement.source()));
        fact.addProperty("confidence", statement.confidenceScore());
        fact.addProperty("valid", statement.isValid());
        return fact;
    }

    private static JsonObject entity(Entity entity) {
        JsonObject item = new JsonObject();
        item.addProperty("uri", entity.uri());
        item.addProperty("type", entity.typeUri());
        item.addProperty("label", entity.label());
        item.addProperty("status", entity.status().name());
        if (entity.scheme() != null) {
            item.addProperty("scheme", entity.scheme());
        }
        if (!entity.altLabels().isEmpty()) {
            JsonArray alt = new JsonArray();
            entity.altLabels().forEach(alt::add);
            item.add("altLabels", alt);
        }
        if (!entity.broader().isEmpty()) {
            JsonArray broader = new JsonArray();
            entity.broader().forEach(broader::add);
            item.add("broader", broader);
        }
        return item;
    }

    private String label(String predicateUri) {
        return ontology.findByUri(predicateUri).map(OntologyTerm::label).orElse(predicateUri);
    }

    private Function<String, Optional<Entity>> entityLookup() {
        Map<String, Optional<Entity>> cache = new HashMap<>();
        return uri -> cache.computeIfAbsent(uri,
                key -> ontology.findEntity(key).or(() -> graph.findEntity(key)));
    }
}
