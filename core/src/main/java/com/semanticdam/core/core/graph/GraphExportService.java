package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;

import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.ontology.Vocabulary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class GraphExportService {
    private final KnowledgeGraphService knowledgeGraphService;
    private final AssetMetadataTripleMapper tripleMapper;
    private final GraphSerializer serializer;

    public GraphExportService(
            KnowledgeGraphService knowledgeGraphService,
            AssetMetadataTripleMapper tripleMapper,
            GraphSerializer serializer) {
        if (knowledgeGraphService == null) {
            throw new IllegalArgumentException("knowledgeGraphService must be provided");
        }
        if (tripleMapper == null) {
            throw new IllegalArgumentException("tripleMapper must be provided");
        }
        if (serializer == null) {
            throw new IllegalArgumentException("serializer must be provided");
        }
        this.knowledgeGraphService = knowledgeGraphService;
        this.tripleMapper = tripleMapper;
        this.serializer = serializer;
    }

    /**
     * The asset's statements as plain triples, each statement's provenance as an
     * {@code rdf:Statement} attributed (PROV-O) to its agent, and the SKOS description of every
     * linked entity, its broader concepts and its scheme.
     */
    public Optional<String> exportAsset(AssetId assetId) {
        Optional<AssetMetadata> metadata = knowledgeGraphService.findByAssetId(assetId);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        List<GraphTriple> triples = new ArrayList<>(tripleMapper.toTriples(metadata.get()));
        StringBuilder provenance = new StringBuilder();
        String subject = SimpleTurtleSerializer.iri(new GraphSubjectResolver().assetSubject(assetId));
        String rdf = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
        Set<String> described = new HashSet<>();
        for (MetadataStatement statement : metadata.get().statements()) {
            String object = statement.objectIsUri() ? SimpleTurtleSerializer.iri(statement.objectValue())
                    : SimpleTurtleSerializer.literal(statement.objectValue());
            provenance.append("[] a <").append(rdf).append("Statement> ; <").append(rdf).append("subject> ").append(subject)
                    .append(" ; <").append(rdf).append("predicate> ").append(SimpleTurtleSerializer.iri(statement.predicateUri()))
                    .append(" ; <").append(rdf).append("object> ").append(object)
                    .append(" ; <").append(Vocabulary.PROV).append("wasAttributedTo> ")
                    .append(SimpleTurtleSerializer.iri(Vocabulary.agentIri(statement.source())))
                    .append(" ; <").append(Vocabulary.SDAM).append("confidence> \"").append(statement.confidenceScore())
                    .append("\"^^<http://www.w3.org/2001/XMLSchema#double> ; <").append(Vocabulary.SDAM).append("valid> ")
                    .append(statement.isValid()).append(" .\n");
            if (statement.objectIsUri() && !Vocabulary.RDF_TYPE.equals(statement.predicateUri())) {
                describe(statement.objectValue(), triples, described);
            }
        }
        return Optional.of(serializer.toTurtle(triples) + provenance);
    }

    private void describe(String uri, List<GraphTriple> triples, Set<String> described) {
        if (uri == null || !described.add(uri)) {
            return;
        }
        knowledgeGraphService.findEntity(uri).ifPresent(entity -> {
            String skos = Vocabulary.SKOS;
            triples.add(new GraphTriple(entity.uri(), Vocabulary.RDF_TYPE, entity.typeUri(), true));
            triples.add(new GraphTriple(entity.uri(), skos + "prefLabel", entity.label(), false));
            entity.altLabels().forEach(label -> triples.add(new GraphTriple(entity.uri(), skos + "altLabel", label, false)));
            entity.hiddenLabels().forEach(label -> triples.add(new GraphTriple(entity.uri(), skos + "hiddenLabel", label, false)));
            entity.broader().forEach(parent -> triples.add(new GraphTriple(entity.uri(), skos + "broader", parent, true)));
            if (entity.scheme() != null) {
                triples.add(new GraphTriple(entity.uri(), skos + "inScheme", entity.scheme(), true));
                if (entity.broader().isEmpty()) {
                    triples.add(new GraphTriple(entity.uri(), skos + "topConceptOf", entity.scheme(), true));
                }
            }
            if (entity.status() != Entity.Status.APPROVED) {
                triples.add(new GraphTriple(entity.uri(), Vocabulary.SDAM + "vocabularyStatus", entity.status().name(), false));
            }
            if (entity.replacedBy() != null) {
                triples.add(new GraphTriple(entity.uri(), Vocabulary.DCTERMS + "isReplacedBy", entity.replacedBy(), true));
            }
            entity.broader().forEach(parent -> describe(parent, triples, described));
            describe(entity.scheme(), triples, described);
        });
    }
}
