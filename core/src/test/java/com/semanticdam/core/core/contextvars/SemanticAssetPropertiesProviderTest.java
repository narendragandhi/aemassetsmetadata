package com.semanticdam.core.core.contextvars;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.InMemoryKnowledgeGraphStore;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(AemContextExtension.class)
class SemanticAssetPropertiesProviderTest {
    @Test
    void resolvesCanonicalAssetFactsThroughTheSameGraph(AemContext context) {
        String sku = "https://example.com/aem-assets-ontology#sku";
        var graph = new InMemoryKnowledgeGraphStore();
        graph.writeAssetMetadata(AssetMetadata.empty(AssetId.of("/content/dam/demo.jpg"))
                .add(MetadataStatement.literal(sku, "SUM-2026-X1", "PIM")));
        var service = new ContextualContentVariableService(
                List.of(new SemanticAssetPropertiesProvider(graph)), List.of());

        assertEquals("SKU: SUM-2026-X1", service.replace("SKU: ((semantic_properties.sku))",
                context.create().resource("/content/dam/demo.jpg"), false));
    }
}
