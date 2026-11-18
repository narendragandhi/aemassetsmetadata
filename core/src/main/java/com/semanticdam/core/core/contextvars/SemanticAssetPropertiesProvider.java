package com.semanticdam.core.core.contextvars;

import com.semanticdam.core.core.application.FactWriteBackService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code ((semantic_properties.<name>))}: governed facts of an asset, for DAM assets and for Sites
 * pages.
 *
 * <ul>
 *   <li>On a DAM asset, the asset itself.</li>
 *   <li>On a page, the asset referenced by the first of the configured page properties that is set
 *       on the page or, failing that, on an ancestor page. Defaults: {@code semanticdam:asset},
 *       then the featured image ({@code cq:featuredimage/fileReference}).</li>
 * </ul>
 *
 * Values are the {@code semanticdam:*} properties written back to the asset's metadata, so they
 * are read with the visitor's own session (an asset they cannot read yields nothing) and work on
 * author and publish alike. Multiple values are joined with ", ".
 */
public final class SemanticAssetPropertiesProvider implements ContextualContentVariableProvider {
    public static final List<String> DEFAULT_PAGE_REFERENCES = List.of("semanticdam:asset", "cq:featuredimage/fileReference");

    private final List<String> pageReferences;

    public SemanticAssetPropertiesProvider() {
        this(DEFAULT_PAGE_REFERENCES);
    }

    public SemanticAssetPropertiesProvider(List<String> pageReferences) {
        this.pageReferences = List.copyOf(pageReferences);
    }

    @Override public String prefix() { return "semantic_properties"; }

    @Override
    public boolean accepts(Resource context) {
        return metadata(context) != null;
    }

    @Override
    public Map<String, Object> properties(Resource context) {
        Map<String, Object> result = new LinkedHashMap<>();
        Resource metadata = metadata(context);
        if (metadata == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : metadata.getValueMap().entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith(FactWriteBackService.PREFIX)) {
                continue;
            }
            Object value = entry.getValue();
            result.put(name.substring(FactWriteBackService.PREFIX.length()),
                    value instanceof Object[] values ? String.join(", ", Arrays.stream(values).map(Object::toString).toList())
                            : String.valueOf(value));
        }
        return result;
    }

    /** The metadata node of the asset this context refers to, read with the context's own resolver. */
    Resource metadata(Resource context) {
        if (context == null) {
            return null;
        }
        String assetPath = context.getPath().startsWith("/content/dam/") ? assetOf(context) : referencedAsset(context);
        if (assetPath == null || !assetPath.startsWith("/content/dam/")) {
            return null;
        }
        return context.getResourceResolver().getResource(assetPath + "/jcr:content/metadata");
    }

    private static String assetOf(Resource resource) {
        int content = resource.getPath().indexOf("/jcr:content");
        return content > 0 ? resource.getPath().substring(0, content) : resource.getPath();
    }

    /** Whether this page's own jcr:content points at the asset through one of the configured properties. */
    public boolean pageReferences(Resource page, String assetPath) {
        Resource content = page == null ? null : page.getChild("jcr:content");
        if (content == null) {
            return false;
        }
        ValueMap values = content.getValueMap();
        for (String reference : pageReferences) {
            if (assetPath.equals(values.get(reference, String.class))) {
                return true;
            }
        }
        return false;
    }

    private String referencedAsset(Resource context) {
        for (Resource page = containingPage(context); page != null; page = containingPage(page.getParent())) {
            Resource content = page.getChild("jcr:content");
            if (content == null) {
                continue;
            }
            ValueMap values = content.getValueMap();
            for (String reference : pageReferences) {
                String path = values.get(reference, String.class);
                if (path != null && !path.isBlank()) {
                    return path;
                }
            }
        }
        return null;
    }

    private static Resource containingPage(Resource resource) {
        Resource current = resource;
        while (current != null && !"cq:Page".equals(current.getValueMap().get("jcr:primaryType", String.class))) {
            current = current.getParent();
        }
        return current;
    }
}
