package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.InMemoryKnowledgeGraphStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetMetadataQueryServiceTest {
    @Test
    void returnsMetadataWhenPresent() {
        InMemoryKnowledgeGraphStore store = new InMemoryKnowledgeGraphStore();
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");
        store.writeAssetMetadata(AssetMetadata.empty(assetId)
                .add(MetadataStatement.literal("http://purl.org/dc/terms/title", "Hero")));

        AssetMetadataQueryService service = new AssetMetadataQueryService(store);

        assertTrue(service.find(assetId).isPresent());
    }
}
