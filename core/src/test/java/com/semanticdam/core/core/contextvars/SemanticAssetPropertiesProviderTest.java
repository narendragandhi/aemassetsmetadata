package com.semanticdam.core.core.contextvars;

import com.semanticdam.core.core.application.FactWriteBackService;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.ontology.Vocabulary;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class SemanticAssetPropertiesProviderTest {
    private static final String ASSET = "/content/dam/demo/hero.jpg";
    private ContextualContentVariableService service;

    @BeforeEach
    void setUp(AemContext context) {
        context.create().resource(ASSET, Map.of("jcr:primaryType", "dam:Asset"));
        context.create().resource(ASSET + "/jcr:content/metadata", Map.of(
                "dc:title", "Hero",
                "semanticdam:sku", new String[] {"SUM-2026"},
                "semanticdam:brand", new String[] {"Summit <Outdoor>"},
                "semanticdam:subject", new String[] {"Cycling", "Italy"},
                "semanticdam:status", "valid"));
        service = new ContextualContentVariableService(List.of(new SemanticAssetPropertiesProvider()), List.of(new UrlEncodeAction()));
    }

    @Test
    void resolvesOnTheAssetItself(AemContext context) {
        assertEquals("SKU SUM-2026, subjects Cycling, Italy", service.replace(
                "SKU ((semantic_properties.sku)), subjects ((semantic_properties.subject))",
                context.resourceResolver().getResource(ASSET), ContextualContentVariableService.Escaping.NONE));
    }

    @Test
    void resolvesOnAPageThroughItsAssetReferenceOrAnAncestors(AemContext context) {
        page(context, "/content/site/en", Map.of("semanticdam:asset", ASSET));
        page(context, "/content/site/en/products", Map.of());
        context.create().resource("/content/site/en/products/jcr:content/root/text", Map.of("text", "x"));

        // a component below an inheriting child page, as the filter sees it
        String html = service.replace("<p>((semantic_properties.brand)) ((semantic_properties.sku!url))</p>",
                context.resourceResolver().getResource("/content/site/en/products/jcr:content/root/text"),
                ContextualContentVariableService.Escaping.HTML);

        assertEquals("<p>Summit &lt;Outdoor&gt; SUM-2026</p>", html);
    }

    @Test
    void fallsBackToTheFeaturedImage(AemContext context) {
        page(context, "/content/site/story", Map.of());
        context.create().resource("/content/site/story/jcr:content/cq:featuredimage", Map.of("fileReference", ASSET));

        assertEquals("SUM-2026", service.replace("((semantic_properties.sku))",
                context.resourceResolver().getResource("/content/site/story"), ContextualContentVariableService.Escaping.HTML));
    }

    @Test
    void leavesTokensWhenThereIsNoAssetOrNoValue(AemContext context) {
        page(context, "/content/site/plain", Map.of());
        page(context, "/content/site/elsewhere", Map.of("semanticdam:asset", "/content/site/not-an-asset"));

        assertEquals("((semantic_properties.sku))", service.replace("((semantic_properties.sku))",
                context.resourceResolver().getResource("/content/site/plain"), ContextualContentVariableService.Escaping.HTML));
        assertEquals("((semantic_properties.sku))", service.replace("((semantic_properties.sku))",
                context.resourceResolver().getResource("/content/site/elsewhere"), ContextualContentVariableService.Escaping.HTML));
        assertEquals("((semantic_properties.campaign))", service.replace("((semantic_properties.campaign))",
                context.resourceResolver().getResource(ASSET), ContextualContentVariableService.Escaping.HTML));
    }

    @Test
    void writeBackPropertiesUseAuthoritativeValidFactsWithLabels() {
        Entity brand = new Entity("https://semanticdam.com/id/brand/summit", Vocabulary.BRAND_CLASS, "Summit");
        Map<String, String[]> properties = FactWriteBackService.properties(List.of(
                MetadataStatement.literal(Vocabulary.SKU, "SUM-2026", "PIM"),
                MetadataStatement.uri(Vocabulary.BRAND, brand.uri(), "AEM"),
                MetadataStatement.literal(Vocabulary.TITLE, "bad", "AEM", false),
                MetadataStatement.uri(Vocabulary.RDF_TYPE, Vocabulary.ASSET, "AEM")),
                uri -> uri.equals(brand.uri()) ? Optional.of(brand) : Optional.empty(), true);

        assertEquals(List.of("semanticdam:sku", "semanticdam:brand", "semanticdam:status"), List.copyOf(properties.keySet()));
        assertArrayEquals(new String[] {"Summit"}, properties.get("semanticdam:brand"));
        assertArrayEquals(new String[] {"violations"}, properties.get("semanticdam:status"));
        assertEquals(List.of("semanticdam:title"), FactWriteBackService.stale(
                List.of("semanticdam:title", "semanticdam:sku", "semanticdam:assetType", "semanticdam:ontologyVersion", "dc:title"),
                properties));
        assertTrue(FactWriteBackService.stale(List.of("semanticdam:status"), properties).isEmpty());
    }

    private static void page(AemContext context, String path, Map<String, Object> properties) {
        context.create().resource(path, Map.of("jcr:primaryType", "cq:Page"));
        context.create().resource(path + "/jcr:content", properties);
    }
}
