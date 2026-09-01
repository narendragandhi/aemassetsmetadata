package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;

public record VectorMatch(AssetId assetId, double score) {
    public VectorMatch {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
    }
}
