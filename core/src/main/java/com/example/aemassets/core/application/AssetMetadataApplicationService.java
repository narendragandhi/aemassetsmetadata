package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.graph.KnowledgeGraphService;
import com.example.aemassets.core.graph.OntologyRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = AssetMetadataApplicationService.class)
public class AssetMetadataApplicationService {
    private final KnowledgeGraphService knowledgeGraphService;
    private final OntologyRegistry ontologyRegistry;

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
