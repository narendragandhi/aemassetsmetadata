package com.example.aemassets.core.graph;

import com.example.aemassets.core.domain.AssetId;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Optional;

@Component(service = GraphExportServiceComponent.class)
public class GraphExportServiceComponent {
    private final GraphExportService exportService;

    public GraphExportServiceComponent(@Reference KnowledgeGraphService knowledgeGraphService) {
        this.exportService = new GraphExportService(
                knowledgeGraphService,
                new AssetMetadataTripleMapper(),
                new SimpleTurtleSerializer()
        );
    }

    public Optional<String> exportAsset(AssetId assetId) {
        return exportService.exportAsset(assetId);
    }
}
