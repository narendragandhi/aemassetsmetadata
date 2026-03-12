package com.example.aemassets.core.graph;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;

import java.util.Optional;

public class SparqlKnowledgeGraphStore implements KnowledgeGraphService {
    private final SparqlClient client;
    private final AssetMetadataTripleMapper tripleMapper;
    private final GraphSerializer serializer;
    private final SparqlQueryBuilder queryBuilder;
    private final SparqlResultParser resultParser;
    private final String graphUri;

    public SparqlKnowledgeGraphStore(
            SparqlClient client,
            AssetMetadataTripleMapper tripleMapper,
            GraphSerializer serializer,
            SparqlQueryBuilder queryBuilder,
            SparqlResultParser resultParser,
            String graphUri) {
        if (client == null) {
            throw new IllegalArgumentException("client must be provided");
        }
        if (tripleMapper == null) {
            throw new IllegalArgumentException("tripleMapper must be provided");
        }
        if (serializer == null) {
            throw new IllegalArgumentException("serializer must be provided");
        }
        if (queryBuilder == null) {
            throw new IllegalArgumentException("queryBuilder must be provided");
        }
        if (resultParser == null) {
            throw new IllegalArgumentException("resultParser must be provided");
        }
        this.client = client;
        this.tripleMapper = tripleMapper;
        this.serializer = serializer;
        this.queryBuilder = queryBuilder;
        this.resultParser = resultParser;
        this.graphUri = graphUri;
    }

    @Override
    public void writeAssetMetadata(AssetMetadata metadata) {
        String turtle = serializer.toTurtle(tripleMapper.toTriples(metadata));
        String update = buildInsert(turtle);
        client.update(update);
    }

    @Override
    public Optional<AssetMetadata> findByAssetId(AssetId assetId) {
        String query = queryBuilder.selectAssetTriples(assetId, graphUri);
        Optional<String> resultBody = client.query(query);
        if (resultBody.isEmpty()) {
            return Optional.empty();
        }
        return resultParser.parse(resultBody.get(), assetId);
    }

    private String buildInsert(String turtle) {
        if (graphUri == null || graphUri.isBlank()) {
            return "INSERT DATA { " + turtle + " }";
        }
        return "INSERT DATA { GRAPH <" + graphUri + "> { " + turtle + " } }";
    }
}
