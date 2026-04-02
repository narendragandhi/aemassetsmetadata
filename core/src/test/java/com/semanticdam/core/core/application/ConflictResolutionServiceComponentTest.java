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
}
