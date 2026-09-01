package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.EmbeddingVector;

import java.util.List;

/**
 * Minimal vector-index contract. Implementations must define replacement and
 * deletion semantics so re-indexing and asset deletion are deterministic.
 */
public interface VectorSearchIndex {
    void upsert(AssetId assetId, EmbeddingVector embedding);

    void remove(AssetId assetId);

    List<VectorMatch> search(EmbeddingVector query, int limit);
}
