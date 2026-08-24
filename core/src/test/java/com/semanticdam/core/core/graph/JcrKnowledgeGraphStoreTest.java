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
}
