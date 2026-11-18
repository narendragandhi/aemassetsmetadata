package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;

import java.util.Map;

/** Writes governed facts onto the asset itself. */
public interface MetadataWriteBack {
    MetadataWriteBack NONE = (assetId, properties, ontologyVersion) -> false;

    /**
     * Sets the given {@code semanticdam:*} properties on the asset's metadata node and removes stale
     * ones. Writes nothing when the node already holds these values.
     *
     * @return whether the repository was changed
     */
    boolean write(AssetId assetId, Map<String, String[]> properties, String ontologyVersion);
}
