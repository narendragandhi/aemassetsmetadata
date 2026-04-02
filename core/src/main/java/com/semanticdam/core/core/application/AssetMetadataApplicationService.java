package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import com.semanticdam.core.core.graph.OntologyRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = AssetMetadataApplicationService.class)
public class AssetMetadataApplicationService {
    private final KnowledgeGraphService knowledgeGraphService;
    private final OntologyRegistry ontologyRegistry;

    @Activate
    public AssetMetadataApplicationService(
            @Reference KnowledgeGraphService knowledgeGraphService,
            @Reference OntologyRegistry ontologyRegistry) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.ontologyRegistry = ontologyRegistry;
    }

    public void indexAssetMetadata(AssetMetadata metadata) {
        knowledgeGraphService.writeAssetMetadata(metadata);
    }

    public OntologyRegistry ontologyRegistry() {
        return ontologyRegistry;
    }
}
