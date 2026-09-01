package com.semanticdam.core.core.contextvars;

import org.apache.sling.api.resource.Resource;

import java.util.Map;

/** Native replacement seam for ACS CCVAR providers. */
public interface ContextualContentVariableProvider {
    String prefix();
    boolean accepts(Resource context);
    Map<String, Object> properties(Resource context);
}
