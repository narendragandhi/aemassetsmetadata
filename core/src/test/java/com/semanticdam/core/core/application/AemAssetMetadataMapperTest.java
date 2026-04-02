package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

class AemAssetMetadataMapperTest {
    @Test
    void mapsCoreFieldsToStatements() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("dc:title", "Hero Image");
        metadata.put("dc:description", "Homepage hero");
        metadata.put("dc:format", "image/png");
        metadata.put("aem:brand", "Northwind");

        MetadataValidator validator = Mockito.mock(MetadataValidator.class);
        Mockito.when(validator.isValid(any())).thenReturn(true);
        AemAssetMetadataMapper mapper = new AemAssetMetadataMapper(validator);
        AssetMetadata result = mapper.map(AssetId.of("/content/dam/site/hero.png"), metadata);

        assertEquals(4, result.statements().size());
    }
}
