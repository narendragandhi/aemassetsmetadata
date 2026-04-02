package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
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
