package com.semanticdam.core.core.contextvars;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class UrlEncodeAction implements ContextualContentVariableAction {
    @Override public String name() { return "url"; }
    @Override public String apply(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
