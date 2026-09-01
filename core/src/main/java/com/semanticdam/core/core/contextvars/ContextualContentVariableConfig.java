package com.semanticdam.core.core.contextvars;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "SemanticDAM - Contextual Content Variables")
public @interface ContextualContentVariableConfig {
    @AttributeDefinition(name = "Enabled") boolean enabled() default false;
    @AttributeDefinition(name = "Rewrite HTML") boolean rewriteHtml() default true;
    @AttributeDefinition(name = "Rewrite model JSON") boolean rewriteModelJson() default true;
}
