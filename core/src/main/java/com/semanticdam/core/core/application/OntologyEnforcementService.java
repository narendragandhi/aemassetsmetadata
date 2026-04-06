package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.OntologyRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OntologyEnforcementService adopts the Drupal "Opinionated Entity" pattern.
 * It ensures that metadata facts adhere to a strict bundle schema.
 */
@Component(service = OntologyEnforcementService.class)
public class OntologyEnforcementService {
    private static final Logger LOG = LoggerFactory.getLogger(OntologyEnforcementService.class);

    @Reference
    private OntologyRegistry ontologyRegistry;

    /**
     * Filters an AssetMetadata object to only include triples allowed for its bundle.
     */
    public AssetMetadata enforce(AssetMetadata metadata, String bundleName) {
        List<MetadataStatement> allowed = metadata.statements().stream()
                .filter(s -> {
                    boolean isAllowed = ontologyRegistry.isAllowedForBundle(s.predicateUri(), bundleName);
                    if (!isAllowed) {
                        LOG.warn("Ontology Violation: Predicate {} not allowed for bundle {}", 
                                s.predicateUri(), bundleName);
                    }
                    return isAllowed;
                })
                .collect(Collectors.toList());

        // Reconstruct metadata with only allowed statements
        AssetMetadata enforced = AssetMetadata.empty(metadata.assetId());
        for (MetadataStatement s : allowed) {
            enforced = enforced.add(s);
        }
        return enforced;
    }
}
