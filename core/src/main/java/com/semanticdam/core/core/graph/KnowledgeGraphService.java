package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;

import java.util.Collection;
import java.util.Optional;

public interface KnowledgeGraphService {
    void writeAssetMetadata(AssetMetadata metadata);

    Optional<AssetMetadata> findByAssetId(AssetId assetId);

    /**
     * Traverses the graph to find all assets matching a specific predicate/object pair.
     * Useful for cross-asset relationship queries (e.g., all assets for a SKU).
     */
    Collection<AssetMetadata> findByPredicateValue(String predicateUri, String objectValue);

    /**
     * Suggests related assets based on metadata similarity (e.g., same SKU, Brand, or Campaign).
     */
    Collection<AssetMetadata> findRelated(AssetId assetId);
}
