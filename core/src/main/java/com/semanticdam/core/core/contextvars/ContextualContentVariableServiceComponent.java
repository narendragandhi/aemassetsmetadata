package com.semanticdam.core.core.contextvars;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects every registered variable provider and action. The references are dynamic and greedy:
 * providers that register after this component (the usual case after a bundle restart) are picked
 * up instead of being silently missing.
 */
@Component(service = ContextualContentVariableServiceComponent.class)
public final class ContextualContentVariableServiceComponent {
    private final List<ContextualContentVariableProvider> providers = new CopyOnWriteArrayList<>();
    private final List<ContextualContentVariableAction> actions = new CopyOnWriteArrayList<>();
    private volatile ContextualContentVariableService delegate = new ContextualContentVariableService(List.of(), List.of());

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    void bindProvider(ContextualContentVariableProvider provider) {
        providers.add(provider);
        rebuild();
    }

    void unbindProvider(ContextualContentVariableProvider provider) {
        providers.remove(provider);
        rebuild();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    void bindAction(ContextualContentVariableAction action) {
        actions.add(action);
        rebuild();
    }

    void unbindAction(ContextualContentVariableAction action) {
        actions.remove(action);
        rebuild();
    }

    private void rebuild() {
        delegate = new ContextualContentVariableService(List.copyOf(providers), List.copyOf(actions));
    }

    public String replace(String input, Resource context, ContextualContentVariableService.Escaping escaping) {
        return delegate.replace(input, context, escaping);
    }
}
