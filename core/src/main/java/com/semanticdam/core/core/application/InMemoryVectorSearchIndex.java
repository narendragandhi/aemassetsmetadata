package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.EmbeddingVector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small reference implementation for tests and local demos.
 *
 * This is intentionally not a production persistence adapter: entries are
 * lost on restart, all queries are linear, and dimension/model compatibility
 * must be enforced by the production adapter.
 */
public final class InMemoryVectorSearchIndex implements VectorSearchIndex {
    private final Map<AssetId, EmbeddingVector> entries = new HashMap<>();
    private final String modelId;
    private final String modelVersion;

    public InMemoryVectorSearchIndex() {
        this("unspecified", "unspecified");
    }

    public InMemoryVectorSearchIndex(String modelId, String modelVersion) {
        if (modelId == null || modelId.isBlank() || modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalArgumentException("index model id and version must be provided");
        }
        this.modelId = modelId;
        this.modelVersion = modelVersion;
    }

    @Override
    public synchronized void upsert(AssetId assetId, EmbeddingVector embedding) {
        if (assetId == null || embedding == null) {
            throw new IllegalArgumentException("assetId and embedding must be provided");
        }
        entries.put(assetId, embedding);
    }

    @Override
    public synchronized void remove(AssetId assetId) {
        if (assetId != null) {
            entries.remove(assetId);
        }
    }

    @Override
    public synchronized List<VectorMatch> search(EmbeddingVector query, int limit) {
        if (query == null) {
            throw new IllegalArgumentException("query must be provided");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<VectorMatch> matches = new ArrayList<>();
        for (Map.Entry<AssetId, EmbeddingVector> entry : entries.entrySet()) {
            if (entry.getValue().dimension() != query.dimension()
                    || !entry.getValue().modelId().equals(query.modelId())
                    || !entry.getValue().modelVersion().equals(query.modelVersion())
                    || !entry.getValue().modelId().equals(modelId)
                    || !entry.getValue().modelVersion().equals(modelVersion)) {
                continue;
            }
            matches.add(new VectorMatch(entry.getKey(), cosineSimilarity(query, entry.getValue())));
        }
        matches.sort(Comparator.comparingDouble(VectorMatch::score).reversed()
                .thenComparing(match -> match.assetId().path()));
        return List.copyOf(matches.subList(0, Math.min(limit, matches.size())));
    }

    private static double cosineSimilarity(EmbeddingVector left, EmbeddingVector right) {
        double dot = 0.0;
        double leftMagnitude = 0.0;
        double rightMagnitude = 0.0;
        for (int index = 0; index < left.dimension(); index++) {
            double leftValue = left.valueAt(index);
            double rightValue = right.valueAt(index);
            dot += leftValue * rightValue;
            leftMagnitude += leftValue * leftValue;
            rightMagnitude += rightValue * rightValue;
        }
        if (leftMagnitude == 0.0 || rightMagnitude == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }
}
