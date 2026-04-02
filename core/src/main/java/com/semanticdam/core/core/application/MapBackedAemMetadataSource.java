package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MapBackedAemMetadataSource implements AemMetadataSource {
    private final Map<AssetId, Map<String, Object>> store = new HashMap<>();

    public void put(AssetId assetId, Map<String, Object> metadata) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must be provided");
        }
        store.put(assetId, new HashMap<>(metadata));
    }

    @Override
    public Map<String, Object> readMetadata(AssetId assetId) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        return store.getOrDefault(assetId, Collections.emptyMap());
    }
}
