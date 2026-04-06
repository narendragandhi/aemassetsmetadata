package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SemanticSeoService transforms Knowledge Graph triples into industry-standard 
 * JSON-LD for SEO injection into AEM pages.
 */
@Component(service = SemanticSeoService.class)
public class SemanticSeoService {

    @Reference
    private KnowledgeGraphService knowledgeGraphService;

    public String getJsonLd(String assetPath) {
        Optional<AssetMetadata> metadataOpt = knowledgeGraphService.findByAssetId(AssetId.of(assetPath));
        if (metadataOpt.isEmpty()) return "";

        AssetMetadata metadata = metadataOpt.get();
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"@context\": \"https://schema.org\",\n");
        json.append("  \"@type\": \"ImageObject\",\n");
        json.append("  \"contentUrl\": \"").append(assetPath).append("\",\n");

        String properties = metadata.statements().stream()
                .filter(s -> s.predicateUri().startsWith("https://schema.org/"))
                .map(s -> String.format("  \"%s\": \"%s\"", 
                        s.predicateUri().substring("https://schema.org/".length()), 
                        escape(s.objectValue())))
                .collect(Collectors.joining(",\n"));

        json.append(properties);
        json.append("\n}");

        return String.format("<script type=\"application/ld+json\">\n%s\n</script>", json.toString());
    }

    private String escape(String value) {
        return value.replace("\"", "\\\"");
    }
}
