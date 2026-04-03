package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Map;

@Component(service = AemAssetMetadataMapper.class)
public class AemAssetMetadataMapper {
    private final MetadataValidator validator;

    @Activate
    public AemAssetMetadataMapper(@Reference MetadataValidator validator) {
        this.validator = validator;
    }

    public AssetMetadata map(AssetId assetId, Map<String, Object> metadata) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must be provided");
        }

        AssetMetadata result = AssetMetadata.empty(assetId);

        // AEM Native / Dublin Core Mapping
        result = addValidated(result, "http://purl.org/dc/terms/title", metadata.get("dc:title"), "AEM");
        result = addValidated(result, "http://purl.org/dc/terms/description", metadata.get("dc:description"), "AEM");
        result = addValidated(result, "http://purl.org/dc/terms/format", metadata.get("dc:format"), "AEM");
        result = addValidated(result, "https://example.com/aem-assets-ontology#brand", metadata.get("aem:brand"), "AEM");

        // PIM Integration Mapping
        result = addValidated(result, "https://example.com/aem-assets-ontology#sku", metadata.get("pim:sku"), "PIM");
        result = addValidated(result, "https://example.com/aem-assets-ontology#pimCategory", metadata.get("pim:category"), "PIM");

        // Workfront Integration Mapping
        result = addValidated(result, "https://example.com/aem-assets-ontology#workfrontProjectId", metadata.get("workfront:projectID"), "Workfront");
        result = addValidated(result, "https://example.com/aem-assets-ontology#belongsToCampaign", metadata.get("workfront:campaignName"), "Workfront");

        return result;
    }

    private AssetMetadata addValidated(AssetMetadata metadata, String predicateUri, Object value, String source) {
        if (value instanceof String && !((String) value).isBlank()) {
            String val = (String) value;
            double confidence = getSourceConfidence(source);
            MetadataStatement statement = MetadataStatement.literal(predicateUri, val, source, true, confidence);
            boolean isValid = validator.isValid(statement);
            return metadata.add(MetadataStatement.literal(predicateUri, val, source, isValid, confidence));
        }
        return metadata;
    }

    private double getSourceConfidence(String source) {
        if ("PIM".equalsIgnoreCase(source)) return 0.95;
        if ("Workfront".equalsIgnoreCase(source)) return 0.85;
        if ("AEM".equalsIgnoreCase(source)) return 0.75;
        return 0.50;
    }
}
