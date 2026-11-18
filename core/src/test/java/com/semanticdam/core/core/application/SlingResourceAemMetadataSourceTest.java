package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class SlingResourceAemMetadataSourceTest {
    private final AemContext context = new AemContext();

    @Test
    void readsMetadataFromAssetPath() {
        context.create().resource("/content/dam/site/hero.png/jcr:content/metadata",
                "dc:title", "Hero",
                "dc:format", "image/png",
                "aem:brand", "Northwind");

        ResourceResolver resolver = context.resourceResolver();
        SlingResourceAemMetadataSource source = new SlingResourceAemMetadataSource(resolver);

        Map<String, Object> metadata = source.readMetadata(AssetId.of("/content/dam/site/hero.png")).orElseThrow();

        assertEquals("Hero", metadata.get("dc:title"));
        assertEquals("image/png", metadata.get("dc:format"));
        assertEquals("Northwind", metadata.get("aem:brand"));
    }

    @Test
    void distinguishesAMissingAssetFromAnAssetWithoutMetadata() {
        context.create().resource("/content/dam/site/bare.png");
        SlingResourceAemMetadataSource source = new SlingResourceAemMetadataSource(context.resourceResolver());

        assertTrue(source.readMetadata(AssetId.of("/content/dam/site/gone.png")).isEmpty());
        assertTrue(source.readMetadata(AssetId.of("/content/dam/site/bare.png")).orElseThrow().isEmpty());
    }
}
