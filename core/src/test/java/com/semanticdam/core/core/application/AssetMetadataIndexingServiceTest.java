package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.InMemoryKnowledgeGraphStore;
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
