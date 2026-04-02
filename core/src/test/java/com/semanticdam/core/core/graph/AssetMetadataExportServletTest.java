package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
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
class AssetMetadataExportServletTest {
    @Test
    void returnsTurtleWhenMetadataPresent(AemContext context) throws Exception {
        InMemoryKnowledgeGraphStore store = new InMemoryKnowledgeGraphStore();
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");
        AssetMetadata metadata = AssetMetadata.empty(assetId)
                .add(MetadataStatement.literal("http://purl.org/dc/terms/title", "Hero"));
        store.writeAssetMetadata(metadata);

        GraphExportServiceComponent exportComponent = new GraphExportServiceComponent(store);
        AssetMetadataExportServlet servlet = new AssetMetadataExportServlet();
        inject(servlet, "exportService", exportComponent);

        MockSlingHttpServletRequest request = context.request();
        request.setParameterMap(java.util.Map.of("path", new String[]{assetId.path()}));
        MockSlingHttpServletResponse response = context.response();

        servlet.doGet(request, response);

        assertEquals("text/turtle;charset=UTF-8", response.getContentType());
        assertTrue(response.getOutputAsString().contains("Hero"));
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
