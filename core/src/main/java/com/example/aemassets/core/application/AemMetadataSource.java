package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;

import java.util.Map;

public interface AemMetadataSource {
    Map<String, Object> readMetadata(AssetId assetId);
}
