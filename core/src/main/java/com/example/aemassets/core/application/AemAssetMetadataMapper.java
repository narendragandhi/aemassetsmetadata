package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.domain.MetadataStatement;

import java.util.Map;

public class AemAssetMetadataMapper {
    public AssetMetadata map(AssetId assetId, Map<String, Object> metadata) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must be provided");
        }

        AssetMetadata result = AssetMetadata.empty(assetId);

        Object title = metadata.get("dc:title");
        if (title instanceof String && !((String) title).isBlank()) {
            result = result.add(MetadataStatement.literal("http://purl.org/dc/terms/title", (String) title));
        }

        Object description = metadata.get("dc:description");
        if (description instanceof String && !((String) description).isBlank()) {
            result = result.add(MetadataStatement.literal("http://purl.org/dc/terms/description", (String) description));
        }

        Object format = metadata.get("dc:format");
        if (format instanceof String && !((String) format).isBlank()) {
            result = result.add(MetadataStatement.literal("http://purl.org/dc/terms/format", (String) format));
        }

        Object brand = metadata.get("aem:brand");
        if (brand instanceof String && !((String) brand).isBlank()) {
            result = result.add(MetadataStatement.literal("https://example.com/aem-assets-ontology#brand", (String) brand));
        }

        return result;
    }
}
