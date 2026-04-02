package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetMetadataTripleMapperTest {
    @Test
    void mapsMetadataToTriples() {
        AssetMetadata metadata = AssetMetadata.empty(AssetId.of("/content/dam/site/hero.png"))
                .add(MetadataStatement.literal("http://purl.org/dc/terms/title", "Hero"))
                .add(MetadataStatement.literal("http://purl.org/dc/terms/format", "image/png"));

        AssetMetadataTripleMapper mapper = new AssetMetadataTripleMapper();
        List<GraphTriple> triples = mapper.toTriples(metadata);

        assertEquals(2, triples.size());
        assertEquals("https://example.com/aem-assets/asset/content/dam/site/hero.png", triples.get(0).subject());
    }
}
