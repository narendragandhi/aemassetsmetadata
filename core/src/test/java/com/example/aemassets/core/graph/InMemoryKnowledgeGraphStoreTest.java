package com.example.aemassets.core.graph;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.domain.MetadataStatement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryKnowledgeGraphStoreTest {
    @Test
    void storesAndRetrievesMetadata() {
        InMemoryKnowledgeGraphStore graphService = new InMemoryKnowledgeGraphStore();
        AssetId assetId = AssetId.of("/content/dam/brand/logo.png");

        AssetMetadata metadata = AssetMetadata.empty(assetId)
                .add(MetadataStatement.literal("http://schema.org/name", "Logo"));

        graphService.writeAssetMetadata(metadata);

        assertTrue(graphService.findByAssetId(assetId).isPresent());
        assertEquals(metadata, graphService.findByAssetId(assetId).get());
    }
}
