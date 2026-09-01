package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.EmbeddingVector;
import com.semanticdam.core.core.graph.KnowledgeGraphService;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference hybrid policy: vector similarity supplies recall, while an exact
 * governed graph filter receives a deterministic boost. The policy is kept in
 * application code so a production search adapter can replace the index.
 */
public final class HybridAssetRetriever {
    private static final double GRAPH_MATCH_BOOST = 0.25;

    private final EmbeddingProvider embeddingProvider;
    private final VectorSearchIndex vectorIndex;
    private final KnowledgeGraphService graphService;

    public HybridAssetRetriever(EmbeddingProvider embeddingProvider,
            VectorSearchIndex vectorIndex, KnowledgeGraphService graphService) {
        if (embeddingProvider == null || vectorIndex == null || graphService == null) {
            throw new IllegalArgumentException("retrieval dependencies must be provided");
        }
        this.embeddingProvider = embeddingProvider;
        this.vectorIndex = vectorIndex;
        this.graphService = graphService;
    }

    public List<HybridRetrievalResult> retrieve(String query, String predicateUri,
            String objectValue, int limit) {
        if (query == null || query.isBlank() || predicateUri == null || predicateUri.isBlank()
                || objectValue == null || objectValue.isBlank()) {
            throw new IllegalArgumentException("query and graph filter values must be provided");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<VectorMatch> vectorMatches = vectorIndex.search(embeddingProvider.embed(query), limit);
        Map<AssetId, Double> vectorScores = new HashMap<>();
        for (VectorMatch match : vectorMatches) {
            vectorScores.put(match.assetId(), match.score());
        }
        Collection<com.semanticdam.core.core.domain.AssetMetadata> graphMatches =
                graphService.findByPredicateValue(predicateUri, objectValue);
        Map<AssetId, Boolean> graphAssetIds = new HashMap<>();
        for (var metadata : graphMatches) {
            graphAssetIds.put(metadata.assetId(), Boolean.TRUE);
        }
        for (AssetId assetId : graphAssetIds.keySet()) {
            vectorScores.putIfAbsent(assetId, 0.0);
        }
        return vectorScores.entrySet().stream()
                .map(entry -> new HybridRetrievalResult(entry.getKey(), entry.getValue(),
                        graphAssetIds.containsKey(entry.getKey()),
                        entry.getValue() + (graphAssetIds.containsKey(entry.getKey()) ? GRAPH_MATCH_BOOST : 0.0)))
                .sorted(java.util.Comparator.comparingDouble(HybridRetrievalResult::score).reversed()
                        .thenComparing(result -> result.assetId().path()))
                .limit(limit)
                .toList();
    }
}
