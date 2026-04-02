package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Optional;

@Component(service = AssetMetadataQueryServiceComponent.class)
public class AssetMetadataQueryServiceComponent {
    private final AssetMetadataQueryService queryService;

    @Activate
    public AssetMetadataQueryServiceComponent(@Reference KnowledgeGraphService knowledgeGraphService) {
        this.queryService = new AssetMetadataQueryService(knowledgeGraphService);
    }

    public Optional<AssetMetadata> find(AssetId assetId) {
        return queryService.find(assetId);
    }
}
