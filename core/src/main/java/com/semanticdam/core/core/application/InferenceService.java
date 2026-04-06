package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * InferenceService derives new semantic facts based on existing triples.
 * Compliments Jessica Talisman's "Reasoning" and Kurt Cagle's "Self-Describing Data".
 */
@Component(service = InferenceService.class)
public class InferenceService {
    private static final Logger LOG = LoggerFactory.getLogger(InferenceService.class);

    /**
     * Analyzes metadata and infers higher-level attributes.
     */
    public AssetMetadata applyInferences(AssetMetadata metadata) {
        List<MetadataStatement> inferences = new ArrayList<>();

        for (MetadataStatement statement : metadata.statements()) {
            // Rule 1: Sustainable Inference
            if (isSustainableCategory(statement)) {
                inferences.add(MetadataStatement.uri(
                    "https://schema.org/category", 
                    "https://semanticdam.com/taxonomy/sustainability#SustainableProduct", 
                    "Inference-Engine"
                ));
            }

            // Rule 2: Campaign Hierarchy Inference
            if (isHighValueCampaign(statement)) {
                inferences.add(MetadataStatement.literal(
                    "https://semanticdam.com/ontology/strategy#priority", 
                    "Tier-1-Global", 
                    "Inference-Engine"
                ));
            }
        }

        AssetMetadata result = metadata;
        for (MetadataStatement inf : inferences) {
            if (!alreadyExists(metadata, inf)) {
                LOG.info("Inferred fact: {} for asset {}", inf.objectValue(), metadata.assetId().path());
                result = result.add(inf);
            }
        }
        return result;
    }

    private boolean isSustainableCategory(MetadataStatement s) {
        return "http://www.w3.org/2004/02/skos/core#prefLabel".equals(s.predicateUri()) &&
               (s.objectValue().contains("Organic") || s.objectValue().contains("Recycled"));
    }

    private boolean isHighValueCampaign(MetadataStatement s) {
        return "https://semanticdam.com/ontology/workfront#campaignName".equals(s.predicateUri()) &&
               s.objectValue().contains("SUMMER_PROMO_2026");
    }

    private boolean alreadyExists(AssetMetadata metadata, MetadataStatement inference) {
        return metadata.statements().stream()
                .anyMatch(s -> s.predicateUri().equals(inference.predicateUri()) && 
                               s.objectValue().equals(inference.objectValue()));
    }
}
