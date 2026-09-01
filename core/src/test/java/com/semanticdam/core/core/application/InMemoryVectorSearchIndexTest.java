package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.EmbeddingVector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryVectorSearchIndexTest {
    @Test
    void ranksByCosineAndUsesPathAsDeterministicTieBreaker() {
        InMemoryVectorSearchIndex index = new InMemoryVectorSearchIndex();
        index.upsert(AssetId.of("/content/dam/b.jpg"), EmbeddingVector.of(1, 0));
        index.upsert(AssetId.of("/content/dam/a.jpg"), EmbeddingVector.of(1, 0));
        index.upsert(AssetId.of("/content/dam/c.jpg"), EmbeddingVector.of(0, 1));

        List<VectorMatch> matches = index.search(EmbeddingVector.of(1, 0), 2);

        assertEquals(List.of("/content/dam/a.jpg", "/content/dam/b.jpg"),
                matches.stream().map(match -> match.assetId().path()).toList());
        assertEquals(1.0, matches.get(0).score(), 0.000001);
    }

    @Test
    void replacesAndRemovesEntries() {
        InMemoryVectorSearchIndex index = new InMemoryVectorSearchIndex();
        AssetId assetId = AssetId.of("/content/dam/a.jpg");
        index.upsert(assetId, EmbeddingVector.of(1, 0));
        index.upsert(assetId, EmbeddingVector.of(0, 1));
        assertEquals(1.0, index.search(EmbeddingVector.of(0, 1), 1).get(0).score(), 0.000001);

        index.remove(assetId);
        assertEquals(0, index.search(EmbeddingVector.of(0, 1), 1).size());
    }

    @Test
    void rejectsInvalidSearchLimitAndEmbeddingValues() {
        assertThrows(IllegalArgumentException.class, () -> EmbeddingVector.of(1, Double.NaN));
        InMemoryVectorSearchIndex index = new InMemoryVectorSearchIndex();
        assertThrows(IllegalArgumentException.class,
                () -> index.search(EmbeddingVector.of(1), 0));
    }

    @Test
    void excludesEmbeddingsFromAnotherModelVersion() {
        InMemoryVectorSearchIndex index = new InMemoryVectorSearchIndex("catalog-model", "2");
        AssetId assetId = AssetId.of("/content/dam/a.jpg");
        index.upsert(assetId, EmbeddingVector.of("catalog-model", "1", 1, 0));

        assertEquals(0, index.search(EmbeddingVector.of("catalog-model", "2", 1, 0), 1).size());
    }
}
