package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConflictResolutionServiceComponentTest {

    private ConflictResolutionServiceComponent service;

    @BeforeEach
    void setUp() {
        service = new ConflictResolutionServiceComponent();
        ConflictResolutionServiceComponent.Config config = mock(ConflictResolutionServiceComponent.Config.class);
        
        // Define precedence: SKU (PIM wins), Title (AEM wins)
        when(config.precedence_rules()).thenReturn(new String[]{
            "https://example.com/aem-assets-ontology#sku=PIM,AEM",
            "http://purl.org/dc/terms/title=AEM,Workfront"
        });
        when(config.default_priority()).thenReturn(new String[]{"AEM", "PIM", "Workfront"});
        
        service.activate(config);
    }

    @Test
    void testResolveConflicts_SkuFromPIMWins() {
        AssetId assetId = AssetId.of("/content/dam/test.jpg");
        AssetMetadata raw = AssetMetadata.empty(assetId)
            .add(MetadataStatement.literal("https://example.com/aem-assets-ontology#sku", "AEM-VAL", "AEM"))
            .add(MetadataStatement.literal("https://example.com/aem-assets-ontology#sku", "PIM-VAL", "PIM"));

        Collection<MetadataStatement> resolved = service.resolve(raw);

        Map<String, List<MetadataStatement>> byPredicate = resolved.stream()
                .collect(Collectors.groupingBy(MetadataStatement::predicateUri));

        assertEquals(1, byPredicate.get("https://example.com/aem-assets-ontology#sku").size());
        assertEquals("PIM-VAL", byPredicate.get("https://example.com/aem-assets-ontology#sku").get(0).objectValue());
        assertEquals("PIM", byPredicate.get("https://example.com/aem-assets-ontology#sku").get(0).source());
    }

    @Test
    void testResolveConflicts_TitleFromAEMWins() {
        AssetId assetId = AssetId.of("/content/dam/test.jpg");
        AssetMetadata raw = AssetMetadata.empty(assetId)
            .add(MetadataStatement.literal("http://purl.org/dc/terms/title", "Workfront Title", "Workfront"))
            .add(MetadataStatement.literal("http://purl.org/dc/terms/title", "AEM Title", "AEM"));

        Collection<MetadataStatement> resolved = service.resolve(raw);

        Map<String, List<MetadataStatement>> byPredicate = resolved.stream()
                .collect(Collectors.groupingBy(MetadataStatement::predicateUri));

        assertEquals(1, byPredicate.get("http://purl.org/dc/terms/title").size());
        assertEquals("AEM Title", byPredicate.get("http://purl.org/dc/terms/title").get(0).objectValue());
        assertEquals("AEM", byPredicate.get("http://purl.org/dc/terms/title").get(0).source());
    }

    @Test
    void keepsEveryValueOfAMultiValuedPredicate() {
        AssetId assetId = AssetId.of("/content/dam/test.jpg");
        String subject = "http://purl.org/dc/terms/subject";
        AssetMetadata raw = AssetMetadata.empty(assetId)
            .add(MetadataStatement.uri(subject, "urn:concept:organic", "PIM", true, 0.95))
            .add(MetadataStatement.uri(subject, "urn:concept:sustainable", "Inference-Engine", true, 0.95))
            .add(MetadataStatement.uri(subject, "urn:concept:organic", "AEM", true, 0.75));

        Collection<MetadataStatement> resolved = service.resolve(raw);

        assertEquals(2, resolved.size());
        assertTrue(resolved.stream().anyMatch(s -> s.objectValue().equals("urn:concept:organic") && s.source().equals("PIM")));
        assertTrue(resolved.stream().anyMatch(s -> s.objectValue().equals("urn:concept:sustainable")));
    }

    @Test
    void treatsMaxCountOnePredicatesAsSingleValuedAndPrefersValidValues() {
        service.setOntology(com.semanticdam.core.core.TestOntology.load());
        AssetId assetId = AssetId.of("/content/dam/test.jpg");
        String brand = "https://schema.org/brand";
        String projectId = "https://semanticdam.com/ontology/workfront#projectId";
        AssetMetadata raw = AssetMetadata.empty(assetId)
            // no precedence rule for brand: the asset shape says sh:maxCount 1, most trusted source wins
            .add(MetadataStatement.uri(brand, "urn:brand:from-aem", "AEM", true, 0.75))
            .add(MetadataStatement.uri(brand, "urn:brand:from-pim", "PIM", true, 0.95))
            // an invalid value never beats a valid one, whatever its source
            .add(MetadataStatement.literal(projectId, "not-hex", "PIM", false, 0.95))
            .add(MetadataStatement.literal(projectId, "0123456789abcdef0123456789abcdef", "AEM", true, 0.75));

        Map<String, List<MetadataStatement>> byPredicate = service.resolve(raw).stream()
                .collect(Collectors.groupingBy(MetadataStatement::predicateUri));

        assertEquals(1, byPredicate.get(brand).size());
        assertEquals("urn:brand:from-pim", byPredicate.get(brand).get(0).objectValue());
        assertEquals("0123456789abcdef0123456789abcdef", byPredicate.get(projectId).get(0).objectValue());
    }
}
