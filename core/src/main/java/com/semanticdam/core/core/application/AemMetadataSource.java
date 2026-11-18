package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;

import java.util.Map;
import java.util.Optional;

/** Reads the DAM metadata properties of an asset. */
public interface AemMetadataSource {
    /**
     * @return the metadata properties, or empty if no asset exists at that path
     * @throws IllegalStateException if the repository cannot be read; callers must not treat a
     *                               failed read as an asset without metadata
     */
    Optional<Map<String, Object>> readMetadata(AssetId assetId);
}
