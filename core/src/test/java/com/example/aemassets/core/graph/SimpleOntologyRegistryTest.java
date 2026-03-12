package com.example.aemassets.core.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleOntologyRegistryTest {
    @Test
    void resolvesTermsFromOntology() {
        OntologySource source = new ClasspathOntologySource("/ontology/asset-ontology.ttl");
        SimpleOntologyRegistry registry = new SimpleOntologyRegistry(source);

        assertTrue(registry.findByLabel("Asset").isPresent());
        assertTrue(registry.findByUri("https://example.com/aem-assets-ontology#Asset").isPresent());
    }
}
