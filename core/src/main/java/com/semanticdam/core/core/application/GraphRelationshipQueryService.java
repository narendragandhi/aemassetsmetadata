package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GraphRelationshipQueryService acts like Drupal's "Views".
 * It provides high-level query methods for AEM components based on graph relationships.
 */
@Component(service = GraphRelationshipQueryService.class)
public class GraphRelationshipQueryService {

    @Reference
    private KnowledgeGraphService graphService;

    /**
     * View: Assets in the same SKU family.
     */
    public Collection<String> getAssetsBySku(String sku) {
        return graphService.findByPredicateValue("https://schema.org/sku", sku).stream()
                .map(m -> m.assetId().path())
                .collect(Collectors.toList());
    }

    /**
     * View: Assets belonging to a specific Campaign.
     */
    public Collection<String> getAssetsByCampaign(String campaignName) {
        return graphService.findByPredicateValue("https://semanticdam.com/ontology/workfront#campaignName", campaignName).stream()
                .map(m -> m.assetId().path())
                .collect(Collectors.toList());
    }

    /**
     * View: Assets with high-authority "Sustainable" status.
     */
    public Collection<String> getSustainableAssets() {
        return graphService.findByPredicateValue(
            "https://schema.org/category", 
            "https://semanticdam.com/taxonomy/sustainability#SustainableProduct").stream()
                .map(m -> m.assetId().path())
                .collect(Collectors.toList());
    }
}
