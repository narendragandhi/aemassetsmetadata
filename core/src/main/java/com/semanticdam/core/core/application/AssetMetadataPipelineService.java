package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;

public class AssetMetadataPipelineService {
    private final AemMetadataSource metadataSource;
    private final AssetMetadataExtractionService extractionService;
    private final AssetMetadataIndexingService indexingService;

    public AssetMetadataPipelineService(
            AemMetadataSource metadataSource,
            AssetMetadataExtractionService extractionService,
            AssetMetadataIndexingService indexingService) {
        if (metadataSource == null) {
            throw new IllegalArgumentException("metadataSource must be provided");
        }
        if (extractionService == null) {
            throw new IllegalArgumentException("extractionService must be provided");
        }
        if (indexingService == null) {
            throw new IllegalArgumentException("indexingService must be provided");
        }
        this.metadataSource = metadataSource;
        this.extractionService = extractionService;
        this.indexingService = indexingService;
    }

    public AssetMetadata ingest(AssetId assetId) {
        AssetMetadata metadata = extractionService.extract(assetId, metadataSource.readMetadata(assetId));
        indexingService.index(metadata);
        return metadata;
    }
}
