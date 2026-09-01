package com.semanticdam.core.core.contextvars;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import java.util.Map;

@Component(service = ContextualContentVariableProvider.class, property = "ccvar.prefix=page_properties")
public final class PagePropertiesProviderComponent implements ContextualContentVariableProvider {
    private final PagePropertiesProvider delegate = new PagePropertiesProvider(false);
    @Override public String prefix() { return delegate.prefix(); }
    @Override public boolean accepts(Resource context) { return delegate.accepts(context); }
    @Override public Map<String, Object> properties(Resource context) { return delegate.properties(context); }
}
