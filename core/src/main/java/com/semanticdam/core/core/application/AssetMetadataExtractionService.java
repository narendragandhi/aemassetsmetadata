package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Map;

@Component(service = AssetMetadataExtractionService.class)
public class AssetMetadataExtractionService {
    private final AemAssetMetadataMapper mapper;

    @Activate
    public AssetMetadataExtractionService(@Reference AemAssetMetadataMapper mapper) {
        this.mapper = mapper;
    }

    public AssetMetadata extract(AssetId assetId, Map<String, Object> aemMetadata) {
        return mapper.map(assetId, aemMetadata);
    }
}
