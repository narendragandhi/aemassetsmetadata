package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.domain.OntologyTerm;
import com.semanticdam.core.core.graph.OntologyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataValidatorTest {

    private MetadataValidator validator;
    private OntologyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = mock(OntologyRegistry.class);
        validator = new MetadataValidator(registry);
    }

    @Test
    void testValidSku() {
        String skuPredicate = "https://example.com/aem-assets-ontology#sku";
        when(registry.findByUri(skuPredicate)).thenReturn(Optional.of(
                new OntologyTerm(skuPredicate, "SKU", "^[A-Z0-9-]+$")
        ));

        MetadataStatement statement = MetadataStatement.literal(skuPredicate, "SUM-2026", "PIM");
        assertTrue(validator.isValid(statement));
    }

    @Test
    void testInvalidSku() {
        String skuPredicate = "https://example.com/aem-assets-ontology#sku";
        when(registry.findByUri(skuPredicate)).thenReturn(Optional.of(
                new OntologyTerm(skuPredicate, "SKU", "^[A-Z0-9-]+$")
        ));

        // Invalid because of lower case
        MetadataStatement statement = MetadataStatement.literal(skuPredicate, "sum-2026", "PIM");
        assertFalse(validator.isValid(statement));
    }

    @Test
    void testValidWorkfrontId() {
        String wfPredicate = "https://example.com/aem-assets-ontology#workfrontProjectId";
        when(registry.findByUri(wfPredicate)).thenReturn(Optional.of(
                new OntologyTerm(wfPredicate, "WF ID", "^[0-9a-f]{32}$")
        ));

        MetadataStatement statement = MetadataStatement.literal(wfPredicate, "0123456789abcdef0123456789abcdef", "Workfront");
        assertTrue(validator.isValid(statement));
    }

    @Test
    void testInvalidWorkfrontId() {
        String wfPredicate = "https://example.com/aem-assets-ontology#workfrontProjectId";
        when(registry.findByUri(wfPredicate)).thenReturn(Optional.of(
                new OntologyTerm(wfPredicate, "WF ID", "^[0-9a-f]{32}$")
        ));

        // Too short
        MetadataStatement statement = MetadataStatement.literal(wfPredicate, "short-id", "Workfront");
        assertFalse(validator.isValid(statement));
    }
}
