package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;

import java.util.Map;

public class AssetMetadataExtractionService {
    private final AemAssetMetadataMapper mapper;

    public AssetMetadataExtractionService(AemAssetMetadataMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must be provided");
        }
        this.mapper = mapper;
    }

    public AssetMetadata extract(AssetId assetId, Map<String, Object> aemMetadata) {
        return mapper.map(assetId, aemMetadata);
    }
}
