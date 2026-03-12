package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.domain.MetadataStatement;
import com.example.aemassets.core.graph.InMemoryKnowledgeGraphStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetMetadataIndexingServiceTest {
    @Test
    void indexesMetadataIntoGraphService() {
        InMemoryKnowledgeGraphStore store = new InMemoryKnowledgeGraphStore();
        AssetMetadataIndexingService service = new AssetMetadataIndexingService(store);

        AssetMetadata metadata = AssetMetadata.empty(AssetId.of("/content/dam/site/hero.png"))
                .add(MetadataStatement.literal("http://purl.org/dc/terms/title", "Hero"));

        service.index(metadata);

        assertTrue(store.findByAssetId(metadata.assetId()).isPresent());
        assertEquals(metadata, store.findByAssetId(metadata.assetId()).get());
    }
}
