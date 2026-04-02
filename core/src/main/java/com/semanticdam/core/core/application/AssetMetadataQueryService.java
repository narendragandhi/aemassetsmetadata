package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.graph.KnowledgeGraphService;

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
