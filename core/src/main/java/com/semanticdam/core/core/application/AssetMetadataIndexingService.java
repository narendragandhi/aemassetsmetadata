package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = AssetMetadataIndexingService.class)
public class AssetMetadataIndexingService {
    private final KnowledgeGraphService knowledgeGraphService;

    @Activate
    public AssetMetadataIndexingService(@Reference KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    public void index(AssetMetadata metadata) {
        knowledgeGraphService.writeAssetMetadata(metadata);
    }
}
