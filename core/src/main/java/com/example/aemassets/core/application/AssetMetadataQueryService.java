package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.graph.KnowledgeGraphService;

import java.util.Optional;

public class AssetMetadataQueryService {
    private final KnowledgeGraphService knowledgeGraphService;

    public AssetMetadataQueryService(KnowledgeGraphService knowledgeGraphService) {
        if (knowledgeGraphService == null) {
            throw new IllegalArgumentException("knowledgeGraphService must be provided");
        }
        this.knowledgeGraphService = knowledgeGraphService;
    }

    public Optional<AssetMetadata> find(AssetId assetId) {
        return knowledgeGraphService.findByAssetId(assetId);
    }
}
