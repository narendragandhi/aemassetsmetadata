package com.semanticdam.core.core.contextvars;

import org.apache.sling.api.resource.Resource;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reads page and jcr:content properties without an ACS dependency. */
public final class PagePropertiesProvider implements ContextualContentVariableProvider {
    private final boolean inherited;

    public PagePropertiesProvider(boolean inherited) { this.inherited = inherited; }

    @Override
    public String prefix() { return inherited ? "inherited_page_properties" : "page_properties"; }

    @Override
    public boolean accepts(Resource context) { return context != null; }

    @Override
    public Map<String, Object> properties(Resource context) {
        Map<String, Object> result = new LinkedHashMap<>();
        Resource page = context;
        while (page != null) {
            Resource content = page.getChild("jcr:content");
            if (content != null) {
                content.getValueMap().forEach(result::putIfAbsent);
                page.getValueMap().forEach(result::putIfAbsent);
                if (!inherited) break;
            }
            page = page.getParent();
        }
        return result;
    }
}
