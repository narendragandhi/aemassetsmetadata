package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.EmbeddingVector;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.InMemoryKnowledgeGraphStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridAssetRetrieverTest {
    @Test
    void graphMatchReceivesBoostAndResultsAreDeduplicated() {
        String sku = "https://example.com/aem-assets-ontology#sku";
        AssetId graphAsset = AssetId.of("/content/dam/graph.jpg");
        AssetId vectorAsset = AssetId.of("/content/dam/vector.jpg");
        InMemoryKnowledgeGraphStore graph = new InMemoryKnowledgeGraphStore();
        graph.writeAssetMetadata(AssetMetadata.empty(graphAsset)
                .add(MetadataStatement.literal(sku, "SUM-2026")));

        InMemoryVectorSearchIndex index = new InMemoryVectorSearchIndex("demo-model", "1");
        index.upsert(graphAsset, EmbeddingVector.of("demo-model", "1", 0.8, 0.6));
        index.upsert(vectorAsset, EmbeddingVector.of("demo-model", "1", 1, 0));
        EmbeddingProvider provider = ignored -> EmbeddingVector.of("demo-model", "1", 1, 0);

        List<HybridRetrievalResult> results = new HybridAssetRetriever(provider, index, graph)
                .retrieve("summer product", sku, "SUM-2026", 2);

        assertEquals(2, results.size());
        assertEquals(graphAsset, results.get(0).assetId());
        assertTrue(results.get(0).graphMatch());
        assertEquals(1.05, results.get(0).score(), 0.000001);
    }
}
