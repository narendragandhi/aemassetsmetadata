package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = AssetMetadataPipeline.class)
public class DefaultAssetMetadataPipeline implements AssetMetadataPipeline {
    private final AemMetadataSource metadataSource;
    private final AssetMetadataExtractionService extractionService;
    private final AssetMetadataIndexingService indexingService;
    private final SemanticEventService eventService;
    private final InferenceService inferenceService;

    @Activate
    public DefaultAssetMetadataPipeline(
            @Reference AemMetadataSource metadataSource,
            @Reference AssetMetadataExtractionService extractionService,
            @Reference AssetMetadataIndexingService indexingService,
            @Reference SemanticEventService eventService,
            @Reference InferenceService inferenceService) {
        this.metadataSource = metadataSource;
        this.extractionService = extractionService;
        this.indexingService = indexingService;
        this.eventService = eventService;
        this.inferenceService = inferenceService;
    }

    @Override
    public AssetMetadata ingest(AssetId assetId) {
        AssetMetadata metadata = extractionService.extract(assetId, metadataSource.readMetadata(assetId));
        
        // Strategic Intelligence: Derive new facts
        metadata = inferenceService.applyInferences(metadata);
        
        indexingService.index(metadata);
        
        // Evaluate for semantic events
        eventService.evaluateAndEmit(metadata);
        
        return metadata;
    }
}
