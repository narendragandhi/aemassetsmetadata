package com.semanticdam.core.core.contextvars;

public interface ContextualContentVariableAction {
    String name();
    String apply(String value);
}
