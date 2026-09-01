package com.semanticdam.core.core.contextvars;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(AemContextExtension.class)
class ContextualContentVariableServiceTest {
    @Test
    void resolvesPageInheritedAndUrlActionVariables(AemContext context) {
        context.create().resource("/content/site", Map.of("jcr:primaryType", "cq:Page"));
        context.create().resource("/content/site/jcr:content", Map.of("title", "Parent title", "jcr:language", "en"));
        context.create().resource("/content/site/en", Map.of("jcr:primaryType", "cq:Page"));
        context.create().resource("/content/site/en/jcr:content", Map.of("title", "Child title"));
        var service = new ContextualContentVariableService(
                List.of(new PagePropertiesProvider(false), new PagePropertiesProvider(true)),
                List.of(new UrlEncodeAction()));

        String result = service.replace("((page_properties.title))|((inherited_page_properties.jcr:language))|((page_properties.title!url))",
                context.resourceResolver().getResource("/content/site/en"), false);

        assertEquals("Child title|en|Child+title", result);
    }

    @Test
    void escapesReplacementValuesForJson(AemContext context) {
        context.create().resource("/content/site", Map.of("jcr:primaryType", "cq:Page"));
        context.create().resource("/content/site/jcr:content", Map.of("title", "A \"quoted\" title"));
        var service = new ContextualContentVariableService(List.of(new PagePropertiesProvider(false)), List.of());
        assertEquals("A \\\"quoted\\\" title", service.replace("((page_properties.title))",
                context.resourceResolver().getResource("/content/site"), true));
    }
}
