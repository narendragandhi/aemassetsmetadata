package com.semanticdam.core.core.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * The bounded, auditable context an AI provider is allowed to see for one asset.
 */
public final class GroundedAssetContext {
    private final AssetMetadata asset;
    private final Collection<AssetMetadata> relatedAssets;
    private final String ontologyVersion;

    public GroundedAssetContext(AssetMetadata asset, Collection<AssetMetadata> relatedAssets, String ontologyVersion) {
        if (asset == null) {
            throw new IllegalArgumentException("asset must be provided");
        }
        if (relatedAssets == null) {
            throw new IllegalArgumentException("relatedAssets must be provided");
        }
        if (ontologyVersion == null || ontologyVersion.isBlank()) {
            throw new IllegalArgumentException("ontologyVersion must be provided");
        }
        this.asset = asset;
        this.relatedAssets = Collections.unmodifiableList(new ArrayList<>(relatedAssets));
        this.ontologyVersion = ontologyVersion;
    }

    public AssetMetadata asset() {
        return asset;
    }

    public Collection<AssetMetadata> relatedAssets() {
        return relatedAssets;
    }

    public String ontologyVersion() {
        return ontologyVersion;
    }
}
