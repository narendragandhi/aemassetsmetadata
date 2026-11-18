package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;

import java.util.Optional;

public interface AssetMetadataPipeline {
    /**
     * Re-reads an asset from the DAM and updates its graph node.
     *
     * @return the stored metadata, or empty if the asset no longer exists and was removed
     */
    Optional<AssetMetadata> ingest(AssetId assetId);

    /** Removes an asset, or every asset below a folder path, from the graph. */
    void remove(AssetId assetId);
}
