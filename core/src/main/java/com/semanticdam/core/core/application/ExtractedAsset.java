package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;

import java.util.Map;

/** The statements mapped from one asset, plus the entity nodes those statements link to. */
public record ExtractedAsset(AssetMetadata metadata, Map<String, Entity> entities) {
    public ExtractedAsset {
        entities = Map.copyOf(entities);
    }
}
