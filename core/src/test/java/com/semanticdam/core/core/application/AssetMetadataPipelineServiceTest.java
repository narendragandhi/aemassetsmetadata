package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.graph.InMemoryKnowledgeGraphStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

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

        MetadataValidator validator = Mockito.mock(MetadataValidator.class);
        Mockito.when(validator.isValid(any())).thenReturn(true);
        AssetMetadataExtractionService extractionService = new AssetMetadataExtractionService(new AemAssetMetadataMapper(validator));
        InMemoryKnowledgeGraphStore store = new InMemoryKnowledgeGraphStore();
        AssetMetadataIndexingService indexingService = new AssetMetadataIndexingService(store);

        AssetMetadataPipelineService pipeline = new AssetMetadataPipelineService(source, extractionService, indexingService);
        AssetMetadata metadata = pipeline.ingest(assetId);

        assertEquals(3, metadata.statements().size());
        assertTrue(store.findByAssetId(assetId).isPresent());
    }
}
