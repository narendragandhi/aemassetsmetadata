package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SparqlKnowledgeGraphStore implements KnowledgeGraphService {
    private final SparqlClient client;
    private final AssetMetadataTripleMapper mapper;
    private final GraphSerializer serializer;
    private final SparqlQueryBuilder queryBuilder;
    private final NoopSparqlResultParser resultParser;
    private final String graphUri;

    public SparqlKnowledgeGraphStore(
            SparqlClient client,
            AssetMetadataTripleMapper mapper,
            GraphSerializer serializer,
            SparqlQueryBuilder queryBuilder,
            NoopSparqlResultParser resultParser,
            String graphUri) {
        this.client = client;
        this.mapper = mapper;
        this.serializer = serializer;
        this.queryBuilder = queryBuilder;
        this.resultParser = resultParser;
        this.graphUri = graphUri;
    }

    @Override
    public void writeAssetMetadata(AssetMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must be provided");
        }
        List<GraphTriple> triples = mapper.toTriples(metadata);
        if (triples.isEmpty()) {
            return;
        }
        String graphClause = graphUri == null || graphUri.isBlank()
                ? ""
                : "GRAPH <" + graphUri + "> ";
        StringBuilder update = new StringBuilder();
        String turtle = serializer.toTurtle(triples);
        for (String line : turtle.split("\\R")) {
            if (line.trim().startsWith("@prefix")) {
                update.append("PREFIX").append(line.trim().substring("@prefix".length())).append('\n');
            }
        }
        update.append("INSERT DATA { ").append(graphClause).append("{ ");
        for (String line : turtle.split("\\R")) {
            if (!line.trim().startsWith("@prefix") && !line.isBlank()) {
                update.append(line).append('\n');
            }
        }
        update.append("} }");
        client.update(update.toString());
    }

    @Override
    public Optional<AssetMetadata> findByAssetId(AssetId assetId) {
        if (assetId == null) {
            return Optional.empty();
        }
        String query = queryBuilder.selectAssetTriples(assetId, graphUri);
        return client.query(query).flatMap(result -> resultParser.parse(result, assetId));
    }

    @Override
    public Collection<AssetMetadata> findByPredicateValue(String predicateUri, String objectValue) {
        if (predicateUri == null || predicateUri.isBlank() || objectValue == null || objectValue.isBlank()) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    @Override
    public Collection<AssetMetadata> findRelated(AssetId assetId) {
        return Collections.emptyList();
    }
}
