package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;

import java.util.Optional;

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

    public Optional<String> exportAsset(AssetId assetId) {
        Optional<AssetMetadata> metadata = knowledgeGraphService.findByAssetId(assetId);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(serializer.toTurtle(tripleMapper.toTriples(metadata.get())));
    }
}
