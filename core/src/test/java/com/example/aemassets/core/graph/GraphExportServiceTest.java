package com.example.aemassets.core.graph;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.domain.MetadataStatement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphExportServiceTest {
    @Test
    void exportsMetadataAsTurtle() {
        InMemoryKnowledgeGraphStore store = new InMemoryKnowledgeGraphStore();
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");
        AssetMetadata metadata = AssetMetadata.empty(assetId)
                .add(MetadataStatement.literal("http://purl.org/dc/terms/title", "Hero"));
        store.writeAssetMetadata(metadata);

        GraphExportService service = new GraphExportService(
                store,
                new AssetMetadataTripleMapper(),
                new SimpleTurtleSerializer()
        );

        String turtle = service.exportAsset(assetId).orElseThrow();

        assertTrue(turtle.contains("Hero"));
        assertTrue(turtle.contains("http://purl.org/dc/terms/title"));
    }
}
