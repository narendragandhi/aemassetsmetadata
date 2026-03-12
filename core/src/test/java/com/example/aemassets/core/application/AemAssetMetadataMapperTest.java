package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AemAssetMetadataMapperTest {
    @Test
    void mapsCoreFieldsToStatements() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("dc:title", "Hero Image");
        metadata.put("dc:description", "Homepage hero");
        metadata.put("dc:format", "image/png");
        metadata.put("aem:brand", "Northwind");

        AemAssetMetadataMapper mapper = new AemAssetMetadataMapper();
        AssetMetadata result = mapper.map(AssetId.of("/content/dam/site/hero.png"), metadata);

        assertEquals(4, result.statements().size());
    }
}
