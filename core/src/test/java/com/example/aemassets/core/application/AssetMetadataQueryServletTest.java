package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.domain.MetadataStatement;
import com.example.aemassets.core.graph.InMemoryKnowledgeGraphStore;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class AssetMetadataQueryServletTest {
    @Test
    void returnsJsonWhenMetadataPresent(AemContext context) throws Exception {
        InMemoryKnowledgeGraphStore store = new InMemoryKnowledgeGraphStore();
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");
        AssetMetadata metadata = AssetMetadata.empty(assetId)
                .add(MetadataStatement.literal("http://purl.org/dc/terms/title", "Hero"));
        store.writeAssetMetadata(metadata);

        AssetMetadataQueryServiceComponent component = new AssetMetadataQueryServiceComponent(store);
        AssetMetadataQueryServlet servlet = new AssetMetadataQueryServlet();
        inject(servlet, "queryService", component);

        MockSlingHttpServletRequest request = context.request();
        request.setParameterMap(java.util.Map.of("path", new String[]{assetId.path()}));
        MockSlingHttpServletResponse response = context.response();

        servlet.doGet(request, response);

        assertEquals("application/json", response.getContentType());
        assertTrue(response.getOutputAsString().contains("\"assetId\""));
        assertTrue(response.getOutputAsString().contains("Hero"));
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
