package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;

public class GraphSubjectResolver {
    private final String baseAssetUri;

    public GraphSubjectResolver() {
        this("https://example.com/aem-assets/asset");
    }

    public GraphSubjectResolver(String baseAssetUri) {
        if (baseAssetUri == null || baseAssetUri.isBlank()) {
            throw new IllegalArgumentException("baseAssetUri must be provided");
        }
        this.baseAssetUri = baseAssetUri;
    }

    public String assetSubject(AssetId assetId) {
        return baseAssetUri + assetId.path();
    }
}
