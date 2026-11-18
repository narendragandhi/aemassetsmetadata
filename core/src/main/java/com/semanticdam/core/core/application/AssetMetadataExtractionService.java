package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Map;

@Component(service = AssetMetadataExtractionService.class)
public class AssetMetadataExtractionService {
    private final AemAssetMetadataMapper mapper;
    private final TagResolver tags;

    @Activate
    public AssetMetadataExtractionService(@Reference AemAssetMetadataMapper mapper, @Reference TagResolver tags) {
        this.mapper = mapper;
        this.tags = tags;
    }

    public AssetMetadataExtractionService(AemAssetMetadataMapper mapper) {
        this(mapper, TagResolver.NONE);
    }

    public ExtractedAsset extract(AssetId assetId, Map<String, Object> aemMetadata) {
        return mapper.extract(assetId, aemMetadata, tags);
    }

    public ExtractedAsset extract(AssetId assetId, Map<String, Object> aemMetadata,
            java.util.function.Function<String, java.util.Optional<com.semanticdam.core.core.domain.Entity>> stored) {
        return mapper.extract(assetId, aemMetadata, tags, stored);
    }
}
