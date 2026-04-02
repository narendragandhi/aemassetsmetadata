package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;

import java.util.Map;

public interface AemMetadataSource {
    Map<String, Object> readMetadata(AssetId assetId);
}
