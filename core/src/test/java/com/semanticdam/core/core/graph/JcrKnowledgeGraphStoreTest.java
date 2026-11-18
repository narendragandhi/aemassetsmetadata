package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class JcrKnowledgeGraphStoreTest {
    @Test
    void roundTripsDuplicateStatementsAndProvenance(AemContext context) throws Exception {
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(context.resourceResolver());
        JcrKnowledgeGraphStore store = new JcrKnowledgeGraphStore(factory);
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");
        AssetMetadata expected = AssetMetadata.empty(assetId)
                .add(MetadataStatement.literal("https://example.com/predicate/title", "Hero\nImage", "AEM", true, 0.8))
                .add(MetadataStatement.uri("https://example.com/predicate/related", "https://example.com/assets/other", "PIM", false, 0.4));

        store.writeAssetMetadata(expected);

        AssetMetadata actual = store.findByAssetId(assetId).orElseThrow();
        assertEquals(expected, actual);
        assertNotNull(context.resourceResolver().getResource(
                "/var/semanticdam/graph/content/dam/site/hero.png/statements/statement-00002"));
    }

    @Test
    void storesEntitiesDeletesAssetsAndReportsFailures(AemContext context) throws Exception {
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(context.resourceResolver());
        JcrKnowledgeGraphStore store = new JcrKnowledgeGraphStore(factory);
        com.semanticdam.core.core.domain.Entity brand = new com.semanticdam.core.core.domain.Entity(
                "https://semanticdam.com/id/brand/acme", "https://schema.org/Brand", "Acme");
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");

        store.writeEntity(brand);
        store.writeEntity(brand);
        store.writeAssetMetadata(AssetMetadata.empty(assetId)
                .add(MetadataStatement.uri("https://schema.org/brand", brand.uri(), "AEM", true, 0.75)));
        assertEquals(brand, store.findEntity(brand.uri()).orElseThrow());
        com.semanticdam.core.core.domain.Entity tag = new com.semanticdam.core.core.domain.Entity(
                "https://semanticdam.com/id/tag/wknd/a/b", "http://www.w3.org/2004/02/skos/core#Concept", "B",
                java.util.List.of("https://semanticdam.com/id/tag/wknd/a"));
        store.writeEntity(tag);
        assertEquals(tag, store.findEntity(tag.uri()).orElseThrow());
        assertEquals(java.util.Optional.empty(), store.findEntity("https://semanticdam.com/id/brand/none"));
        assertEquals("semanticdam/graph/statement", context.resourceResolver().getResource(
                "/var/semanticdam/graph/content/dam/site/hero.png/statements/statement-00001")
                .getValueMap().get("sling:resourceType", String.class));

        store.deleteAsset(assetId);
        assertEquals(java.util.Optional.empty(), store.findByAssetId(assetId));
        assertNotNull(context.resourceResolver().getResource(JcrKnowledgeGraphStore.entityPath(brand.uri())));
    }

    @Test
    void failsLoudlyWhenTheServiceUserCannotLogIn() throws Exception {
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        when(factory.getServiceResourceResolver(anyMap()))
                .thenThrow(new org.apache.sling.api.resource.LoginException("no mapping"));
        JcrKnowledgeGraphStore store = new JcrKnowledgeGraphStore(factory);
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");

        org.junit.jupiter.api.Assertions.assertThrows(GraphStoreException.class, () -> store.findByAssetId(assetId));
        org.junit.jupiter.api.Assertions.assertThrows(GraphStoreException.class,
                () -> store.writeAssetMetadata(AssetMetadata.empty(assetId)));
        org.junit.jupiter.api.Assertions.assertThrows(GraphStoreException.class,
                () -> store.findReferencing("https://semanticdam.com/id/brand/acme"));
    }
}
