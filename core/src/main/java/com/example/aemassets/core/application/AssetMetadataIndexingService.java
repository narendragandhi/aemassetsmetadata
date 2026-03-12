package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.graph.KnowledgeGraphService;

public class AssetMetadataIndexingService {
    private final KnowledgeGraphService knowledgeGraphService;

    public AssetMetadataIndexingService(KnowledgeGraphService knowledgeGraphService) {
        if (knowledgeGraphService == null) {
            throw new IllegalArgumentException("knowledgeGraphService must be provided");
        }
        this.knowledgeGraphService = knowledgeGraphService;
    }

    public void index(AssetMetadata metadata) {
        knowledgeGraphService.writeAssetMetadata(metadata);
    }
}
