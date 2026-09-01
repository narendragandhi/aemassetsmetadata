package com.semanticdam.core.core.contextvars;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import java.util.Collection;

@Component(service = ContextualContentVariableServiceComponent.class)
public final class ContextualContentVariableServiceComponent {
    private ContextualContentVariableService delegate;

    @Reference(cardinality = ReferenceCardinality.MULTIPLE)
    private Collection<ContextualContentVariableProvider> providers;
    @Reference(cardinality = ReferenceCardinality.MULTIPLE)
    private Collection<ContextualContentVariableAction> actions;

    @org.osgi.service.component.annotations.Activate
    void activate() { delegate = new ContextualContentVariableService(providers, actions); }

    public String replace(String input, Resource context, boolean json) {
        if (delegate == null) throw new IllegalStateException("CCVAR service is not active");
        return delegate.replace(input, context, json);
    }
}
