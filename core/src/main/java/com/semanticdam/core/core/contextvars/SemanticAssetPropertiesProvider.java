package com.semanticdam.core.core.contextvars;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import org.apache.sling.api.resource.Resource;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exposes governed SemanticDAM facts to contextual authored text. */
public final class SemanticAssetPropertiesProvider implements ContextualContentVariableProvider {
    private final KnowledgeGraphService graphService;

    public SemanticAssetPropertiesProvider(KnowledgeGraphService graphService) {
        if (graphService == null) throw new IllegalArgumentException("graphService must be provided");
        this.graphService = graphService;
    }

    @Override public String prefix() { return "semantic_properties"; }

    @Override
    public boolean accepts(Resource context) {
        return context != null && graphService.findByAssetId(AssetId.of(context.getPath())).isPresent();
    }

    @Override
    public Map<String, Object> properties(Resource context) {
        Map<String, Object> result = new LinkedHashMap<>();
        graphService.findByAssetId(AssetId.of(context.getPath())).ifPresent(metadata ->
                metadata.statements().forEach(statement -> {
                    String predicate = statement.predicateUri();
                    int fragment = predicate.lastIndexOf('#');
                    int slash = predicate.lastIndexOf('/');
                    String localName = predicate.substring(Math.max(fragment, slash) + 1);
                    result.putIfAbsent(localName, statement.objectValue());
                }));
        return result;
    }
}
