package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.ontology.Vocabulary;
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
    void relatesAssetsThroughSharedEntitiesOnly() {
        String product = "https://semanticdam.com/id/product/sum-2026";
        String campaign = "https://semanticdam.com/id/campaign/summer-promo";
        String format = "http://purl.org/dc/terms/format";

        store.writeAssetMetadata(AssetMetadata.empty(AssetId.of("/content/dam/asset1.jpg"))
                .add(MetadataStatement.uri(Vocabulary.DEPICTS_PRODUCT, product, "PIM"))
                .add(MetadataStatement.uri(Vocabulary.PART_OF_CAMPAIGN, campaign, "Workfront"))
                .add(MetadataStatement.uri(Vocabulary.RDF_TYPE, Vocabulary.ASSET, "AEM"))
                .add(MetadataStatement.literal(format, "image/jpeg", "AEM")));
        store.writeAssetMetadata(AssetMetadata.empty(AssetId.of("/content/dam/asset2.jpg"))
                .add(MetadataStatement.uri(Vocabulary.DEPICTS_PRODUCT, product, "PIM")));
        store.writeAssetMetadata(AssetMetadata.empty(AssetId.of("/content/dam/asset3.jpg"))
                .add(MetadataStatement.uri(Vocabulary.PART_OF_CAMPAIGN, campaign, "Workfront")));
        // Sharing a mime type or a class does not make two assets related.
        store.writeAssetMetadata(AssetMetadata.empty(AssetId.of("/content/dam/other.jpg"))
                .add(MetadataStatement.uri(Vocabulary.RDF_TYPE, Vocabulary.ASSET, "AEM"))
                .add(MetadataStatement.literal(format, "image/jpeg", "AEM")));

        Collection<AssetMetadata> related = store.findRelated(AssetId.of("/content/dam/asset1.jpg"));

        assertEquals(java.util.List.of("/content/dam/asset2.jpg", "/content/dam/asset3.jpg"),
                related.stream().map(metadata -> metadata.assetId().path()).sorted().toList());
        assertEquals(2, store.findReferencing(product).size());
        assertTrue(store.findReferencing(Vocabulary.ASSET).isEmpty());
    }

    @Test
    void storesEntitiesAndDeletesAssets() {
        Entity brand = new Entity("https://semanticdam.com/id/brand/acme", Vocabulary.BRAND_CLASS, "Acme");
        AssetId assetId = AssetId.of("/content/dam/asset1.jpg");
        store.writeEntity(brand);
        store.writeAssetMetadata(AssetMetadata.empty(assetId).add(MetadataStatement.uri(Vocabulary.BRAND, brand.uri())));

        store.deleteAsset(assetId);

        assertTrue(store.findByAssetId(assetId).isEmpty());
        assertEquals(brand, store.findEntity(brand.uri()).orElseThrow());
        assertFalse(store.findEntity("https://semanticdam.com/id/brand/unknown").isPresent());
    }
}
