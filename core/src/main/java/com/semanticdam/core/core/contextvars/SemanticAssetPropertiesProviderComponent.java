package com.semanticdam.core.core.contextvars;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.List;
import java.util.Map;

@Component(service = ContextualContentVariableProvider.class, property = "ccvar.prefix=semantic_properties")
@Designate(ocd = SemanticAssetPropertiesProviderComponent.Config.class)
public final class SemanticAssetPropertiesProviderComponent implements ContextualContentVariableProvider {

    @ObjectClassDefinition(name = "SemanticDAM - semantic_properties variables")
    public @interface Config {
        @AttributeDefinition(name = "Page properties that reference the asset",
                description = "Relative to the page's jcr:content, checked in order on the page and its ancestors.")
        String[] pageReferences() default {"semanticdam:asset", "cq:featuredimage/fileReference"};
    }

    private volatile SemanticAssetPropertiesProvider delegate = new SemanticAssetPropertiesProvider();

    @Activate
    @Modified
    void activate(Config config) {
        delegate = new SemanticAssetPropertiesProvider(List.of(config.pageReferences()));
    }

    @Override public String prefix() { return delegate.prefix(); }
    @Override public boolean accepts(Resource context) { return delegate.accepts(context); }
    @Override public Map<String, Object> properties(Resource context) { return delegate.properties(context); }
}
