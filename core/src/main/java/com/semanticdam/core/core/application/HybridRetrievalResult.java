package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;

public record HybridRetrievalResult(AssetId assetId, double vectorScore, boolean graphMatch, double score) {
    public HybridRetrievalResult {
        if (assetId == null || !Double.isFinite(vectorScore) || !Double.isFinite(score)) {
            throw new IllegalArgumentException("result values must be valid");
        }
    }
}
