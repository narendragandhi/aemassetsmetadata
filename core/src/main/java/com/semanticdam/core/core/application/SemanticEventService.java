package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service that demonstrates State-Based Event-Driven Architecture.
 * It "emits" events only when the asset metadata reaches a business-ready state.
 */
@Component(service = SemanticEventService.class)
public class SemanticEventService {
    private static final Logger LOG = LoggerFactory.getLogger(SemanticEventService.class);

    private static final String SKU_PREDICATE = "https://example.com/aem-assets-ontology#sku";
    private static final String CAMPAIGN_PREDICATE = "https://example.com/aem-assets-ontology#belongsToCampaign";

    public void evaluateAndEmit(AssetMetadata metadata) {
        Set<String> validPredicates = metadata.statements().stream()
                .filter(MetadataStatement::isValid)
                .map(MetadataStatement::predicateUri)
                .collect(Collectors.toSet());

        if (validPredicates.contains(SKU_PREDICATE) && validPredicates.contains(CAMPAIGN_PREDICATE)) {
            emitAssetReadyForAJO(metadata);
        }
    }

    private void emitAssetReadyForAJO(AssetMetadata metadata) {
        // In a real implementation, this would send an event to Adobe I/O Events
        LOG.info(">>> SEMANTIC EVENT: Asset {} is now READY for Adobe Journey Optimizer (AJO). " +
                "Reason: Valid SKU and Campaign present in Knowledge Graph.", metadata.assetId().path());
    }
}
