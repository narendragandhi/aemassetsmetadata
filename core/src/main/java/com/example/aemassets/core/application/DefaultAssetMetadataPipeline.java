package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.graph.KnowledgeGraphService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = AssetMetadataPipeline.class)
public class DefaultAssetMetadataPipeline implements AssetMetadataPipeline {
    private final AemMetadataSource metadataSource;
    private final AssetMetadataExtractionService extractionService;
    private final AssetMetadataIndexingService indexingService;

    public DefaultAssetMetadataPipeline(
            @Reference AemMetadataSource metadataSource,
            @Reference KnowledgeGraphService knowledgeGraphService) {
        this.metadataSource = metadataSource;
        this.extractionService = new AssetMetadataExtractionService(new AemAssetMetadataMapper());
        this.indexingService = new AssetMetadataIndexingService(knowledgeGraphService);
    }

    @Override
    public AssetMetadata ingest(AssetId assetId) {
        AssetMetadata metadata = extractionService.extract(assetId, metadataSource.readMetadata(assetId));
        indexingService.index(metadata);
        return metadata;
    }
}
