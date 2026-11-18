package com.semanticdam.core.core.contextvars;

import org.apache.sling.api.resource.Resource;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the ACS-compatible CCVAR token syntax without ACS AEM Commons. */
public final class ContextualContentVariableService {
    private static final Pattern TOKEN = Pattern.compile("\\(\\(([A-Za-z0-9_-]+)\\.([^!\\)]+)(?:!([A-Za-z0-9_-]+))?\\)\\)");
    private final Map<String, ContextualContentVariableProvider> providers;
    private final Map<String, ContextualContentVariableAction> actions;

    public ContextualContentVariableService(Collection<ContextualContentVariableProvider> providers,
            Collection<ContextualContentVariableAction> actions) {
        if (providers == null || actions == null) throw new IllegalArgumentException("providers and actions are required");
        this.providers = new HashMap<>();
        providers.forEach(provider -> this.providers.put(provider.prefix(), provider));
        this.actions = new HashMap<>();
        actions.forEach(action -> this.actions.put(action.name(), action));
    }

    /** How a value is escaped for the document it is inserted into. */
    public enum Escaping { NONE, HTML, JSON }

    /** @deprecated use {@link #replace(String, Resource, Escaping)}; this form does not escape HTML */
    @Deprecated
    public String replace(String input, Resource context, boolean json) {
        return replace(input, context, json ? Escaping.JSON : Escaping.NONE);
    }

    public String replace(String input, Resource context, Escaping escaping) {
        if (input == null || context == null) return input;
        Matcher matcher = TOKEN.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = value(matcher.group(1), matcher.group(2), matcher.group(3), context);
            if (replacement == null) { matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group())); continue; }
            if (escaping == Escaping.JSON) replacement = jsonEscape(replacement);
            if (escaping == Escaping.HTML) replacement = htmlEscape(replacement);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String value(String prefix, String property, String action, Resource context) {
        ContextualContentVariableProvider provider = providers.get(prefix);
        if (provider == null || !provider.accepts(context)) return null;
        Object raw = provider.properties(context).get(property);
        if (raw == null) return null;
        String value = raw instanceof Object[] values ? java.util.Arrays.stream(values).findFirst().map(Object::toString).orElse("") : raw.toString();
        if (action != null) {
            ContextualContentVariableAction transform = actions.get(action);
            if (transform == null) return null;
            value = transform.apply(value);
        }
        return value;
    }

    private static String htmlEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t");
    }
}
