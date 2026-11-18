package com.semanticdam.core.core.application;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class JcrTagResolverTest {
    @Test
    void resolvesTagsAndAncestorsBelowTheNamespace(AemContext context) throws Exception {
        context.create().tag("wknd:region/emea/italy");
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(context.resourceResolver());

        Map<String, TagResolver.TagInfo> tags = new JcrTagResolver(factory)
                .resolve(List.of("wknd:region/emea/italy", "wknd:missing"));

        assertEquals(List.of("wknd:region/emea/italy", "wknd:region/emea", "wknd:region"), List.copyOf(tags.keySet()));
        assertEquals("wknd:region/emea", tags.get("wknd:region/emea/italy").parentId());
        assertEquals(null, tags.get("wknd:region").parentId());
    }
}
