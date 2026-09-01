package com.semanticdam.core.core.contextvars;

import org.osgi.service.component.annotations.Component;

@Component(service = ContextualContentVariableAction.class, property = "ccvar.action=url")
public final class UrlEncodeActionComponent implements ContextualContentVariableAction {
    private final UrlEncodeAction delegate = new UrlEncodeAction();
    @Override public String name() { return delegate.name(); }
    @Override public String apply(String value) { return delegate.apply(value); }
}
