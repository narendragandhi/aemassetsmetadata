package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.graph.KnowledgeGraphService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Optional;

@Component(service = AssetMetadataQueryServiceComponent.class)
public class AssetMetadataQueryServiceComponent {
    private final AssetMetadataQueryService queryService;

    public AssetMetadataQueryServiceComponent(@Reference KnowledgeGraphService knowledgeGraphService) {
        this.queryService = new AssetMetadataQueryService(knowledgeGraphService);
    }

    public Optional<AssetMetadata> find(AssetId assetId) {
        return queryService.find(assetId);
    }
}
