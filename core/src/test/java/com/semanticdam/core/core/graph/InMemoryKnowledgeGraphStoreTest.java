package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryKnowledgeGraphStoreTest {

    private InMemoryKnowledgeGraphStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryKnowledgeGraphStore();
    }

    @Test
    void testFindByPredicateValue() {
        String skuPredicate = "https://example.com/aem-assets-ontology#sku";
        
        AssetMetadata asset1 = AssetMetadata.empty(AssetId.of("/content/dam/asset1.jpg"))
                .add(MetadataStatement.literal(skuPredicate, "SUM-2026", "PIM"));
        
        AssetMetadata asset2 = AssetMetadata.empty(AssetId.of("/content/dam/asset2.jpg"))
                .add(MetadataStatement.literal(skuPredicate, "SUM-2026", "PIM"));

        AssetMetadata asset3 = AssetMetadata.empty(AssetId.of("/content/dam/asset3.jpg"))
                .add(MetadataStatement.literal(skuPredicate, "OTHER-SKU", "PIM"));

        store.writeAssetMetadata(asset1);
        store.writeAssetMetadata(asset2);
        store.writeAssetMetadata(asset3);

        Collection<AssetMetadata> related = store.findByPredicateValue(skuPredicate, "SUM-2026");

        assertEquals(2, related.size());
        assertTrue(related.stream().anyMatch(a -> a.assetId().path().equals("/content/dam/asset1.jpg")));
        assertTrue(related.stream().anyMatch(a -> a.assetId().path().equals("/content/dam/asset2.jpg")));
        assertFalse(related.stream().anyMatch(a -> a.assetId().path().equals("/content/dam/asset3.jpg")));
    }

    @Test
    void testFindRelated() {
        String skuPredicate = "https://example.com/aem-assets-ontology#sku";
        String campaignPredicate = "https://example.com/aem-assets-ontology#belongsToCampaign";

        AssetMetadata asset1 = AssetMetadata.empty(AssetId.of("/content/dam/asset1.jpg"))
                .add(MetadataStatement.literal(skuPredicate, "SUM-2026", "PIM"))
                .add(MetadataStatement.literal(campaignPredicate, "SUMMER_PROMO", "Workfront"));

        AssetMetadata asset2 = AssetMetadata.empty(AssetId.of("/content/dam/asset2.jpg"))
                .add(MetadataStatement.literal(skuPredicate, "SUM-2026", "PIM")); // Related via SKU

        AssetMetadata asset3 = AssetMetadata.empty(AssetId.of("/content/dam/asset3.jpg"))
                .add(MetadataStatement.literal(campaignPredicate, "SUMMER_PROMO", "Workfront")); // Related via Campaign

        AssetMetadata unrelated = AssetMetadata.empty(AssetId.of("/content/dam/other.jpg"))
                .add(MetadataStatement.literal(skuPredicate, "WINTER-2026", "PIM"));

        store.writeAssetMetadata(asset1);
        store.writeAssetMetadata(asset2);
        store.writeAssetMetadata(asset3);
        store.writeAssetMetadata(unrelated);

        Collection<AssetMetadata> suggestions = store.findRelated(AssetId.of("/content/dam/asset1.jpg"));

        assertEquals(2, suggestions.size(), "Should find 2 related assets");
        assertTrue(suggestions.stream().anyMatch(a -> a.assetId().path().equals("/content/dam/asset2.jpg")), "Should find asset related by SKU");
        assertTrue(suggestions.stream().anyMatch(a -> a.assetId().path().equals("/content/dam/asset3.jpg")), "Should find asset related by Campaign");
        assertFalse(suggestions.stream().anyMatch(a -> a.assetId().path().equals("/content/dam/other.jpg")), "Should not find unrelated asset");
    }
}
