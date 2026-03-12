package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.domain.MetadataStatement;
import com.example.aemassets.core.graph.InMemoryKnowledgeGraphStore;
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
