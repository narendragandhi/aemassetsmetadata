package com.semanticdam.core.core.contextvars;

import com.semanticdam.core.core.graph.KnowledgeGraphService;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Map;

@Component(service = ContextualContentVariableProvider.class, property = "ccvar.prefix=semantic_properties")
public final class SemanticAssetPropertiesProviderComponent implements ContextualContentVariableProvider {
    @Reference
    private KnowledgeGraphService graphService;

    @Override public String prefix() { return "semantic_properties"; }
    @Override public boolean accepts(Resource context) { return delegate().accepts(context); }
    @Override public Map<String, Object> properties(Resource context) { return delegate().properties(context); }
    private SemanticAssetPropertiesProvider delegate() { return new SemanticAssetPropertiesProvider(graphService); }
}
