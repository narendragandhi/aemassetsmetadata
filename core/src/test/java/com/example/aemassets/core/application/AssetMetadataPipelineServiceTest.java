package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.graph.InMemoryKnowledgeGraphStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetMetadataPipelineServiceTest {
    @Test
    void ingestsFromSourceToGraph() {
        MapBackedAemMetadataSource source = new MapBackedAemMetadataSource();
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");
        source.put(assetId, Map.of(
                "dc:title", "Hero Image",
                "dc:format", "image/png",
                "aem:brand", "Northwind"
        ));

        AssetMetadataExtractionService extractionService = new AssetMetadataExtractionService(new AemAssetMetadataMapper());
        InMemoryKnowledgeGraphStore store = new InMemoryKnowledgeGraphStore();
        AssetMetadataIndexingService indexingService = new AssetMetadataIndexingService(store);

        AssetMetadataPipelineService pipeline = new AssetMetadataPipelineService(source, extractionService, indexingService);
        AssetMetadata metadata = pipeline.ingest(assetId);

        assertEquals(3, metadata.statements().size());
        assertTrue(store.findByAssetId(assetId).isPresent());
    }
}
