package com.semanticdam.core.core.ontology;

import com.semanticdam.core.core.TestOntology;
import com.semanticdam.core.core.domain.OntologyTerm;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OntologyModelTest {
    private static final String SUSTAIN = "https://semanticdam.com/taxonomy/sustainability#";
    private final OntologyModel ontology = TestOntology.load();

    @Test
    void exposesVersionTermsAndKinds() {
        assertEquals("3.0.0+profile-1", ontology.version());
        assertEquals(OntologyTerm.Kind.CLASS, ontology.findByUri(Vocabulary.ASSET).orElseThrow().kind());
        assertEquals(OntologyTerm.Kind.DATATYPE_PROPERTY, ontology.findByUri(Vocabulary.SKU).orElseThrow().kind());
        OntologyTerm brand = ontology.findByUri(Vocabulary.BRAND).orElseThrow();
        assertEquals(OntologyTerm.Kind.OBJECT_PROPERTY, brand.kind());
        assertEquals(Vocabulary.BRAND_CLASS, brand.range().orElseThrow());
        assertEquals("^[A-Z0-9-]+$", ontology.findByUri(Vocabulary.SKU).orElseThrow().validationPattern().orElseThrow());
        assertEquals(Vocabulary.ASSET, ontology.findByLabel("asset").orElseThrow().uri());
    }

    /** The ontology is the single source of truth: Java may not name a term it does not declare. */
    @Test
    void declaresEveryTermNamedInVocabulary() throws Exception {
        Set<String> namespacesOnly = Set.of("SDAM", "SCHEMA", "DCTERMS", "SKOS", "RDFS", "OWL", "SH", "PROV",
                "AGENT_NAMESPACE", "RDF_TYPE", "PREF_LABEL", "INFERENCE_SOURCE", "AI_SOURCE_PREFIX");
        for (Field field : Vocabulary.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || namespacesOnly.contains(field.getName())) {
                continue;
            }
            String uri = (String) field.get(null);
            assertTrue(ontology.findByUri(uri).isPresent(),
                    "Vocabulary." + field.getName() + " <" + uri + "> is not declared in asset-ontology.ttl");
        }
    }

    @Test
    void everyMappingAndShapePathUsesADeclaredPredicate() {
        for (PropertyMapping mapping : ontology.mappings()) {
            assertTrue(mapping.mapsToClass() || ontology.findByUri(mapping.predicateUri()).isPresent(),
                    "mapping for " + mapping.sourceProperty() + " uses undeclared " + mapping.predicateUri());
            mapping.entityClass().ifPresent(entityClass -> assertTrue(
                    ontology.instanceNamespace(entityClass).isPresent(), entityClass + " has no instance namespace"));
        }
        for (NodeShape shape : ontology.shapesFor(List.of("https://semanticdam.com/ontology/core#ProductImage"))) {
            for (PropertyShape property : shape.properties()) {
                assertTrue(ontology.findByUri(property.path()).isPresent(),
                        shape.uri() + " constrains undeclared " + property.path());
            }
        }
        assertEquals(11, ontology.mappings().size());
        assertEquals(3, ontology.sourceSystems().size());
    }

    @Test
    void resolvesClassAndConceptHierarchies() {
        String productImage = Vocabulary.SDAM + "ProductImage";
        assertEquals(Set.of(productImage, Vocabulary.ASSET), ontology.superClasses(productImage));
        assertEquals(Set.of(SUSTAIN + "SustainableProduct"), ontology.broaderConcepts(SUSTAIN + "OrganicCotton"));
        assertTrue(ontology.broaderConcepts(SUSTAIN + "SustainableProduct").isEmpty());
        // shapes are inherited: a ProductImage is validated by the Asset shape too
        assertEquals(2, ontology.shapesFor(List.of(productImage)).size());
        assertEquals(1, ontology.shapesFor(List.of(Vocabulary.ASSET)).size());
    }

    @Test
    void reconcilesEntitiesByPreferredAndAlternativeLabel() {
        assertEquals(SUSTAIN + "OrganicCotton",
                ontology.findEntityByLabel(Vocabulary.CONCEPT_CLASS, "organic cotton").orElseThrow().uri());
        assertEquals(SUSTAIN + "OrganicCotton",
                ontology.findEntityByLabel(Vocabulary.CONCEPT_CLASS, "Organic").orElseThrow().uri());
        assertTrue(ontology.findEntityByLabel(Vocabulary.CONCEPT_CLASS, "Denim").isEmpty());
        assertTrue(ontology.findEntityByLabel(Vocabulary.BRAND_CLASS, "Organic").isEmpty());
    }

    @Test
    void closedShapesDecideWhatABundleAllows() {
        assertTrue(ontology.isAllowedForBundle(Vocabulary.SKU, "ProductImage"));
        assertTrue(ontology.isAllowedForBundle(Vocabulary.SKU, "Product Image"));
        assertFalse(ontology.isAllowedForBundle("https://example.com/unknown", "ProductImage"));
        // the base Asset shape is open
        assertTrue(ontology.isAllowedForBundle("https://example.com/unknown", "Asset"));
        assertTrue(ontology.isAllowedForBundle("https://example.com/unknown", "NoSuchBundle"));
    }

    @Test
    void readinessShapesAreNotAppliedByClass() {
        assertEquals(List.of("AJO"), ontology.readinessShapes().stream().map(NodeShape::label).toList());
        assertTrue(ontology.shapesFor(List.of(Vocabulary.ASSET)).stream().noneMatch(NodeShape::readiness));
    }

    @Test
    void mergesExtensionDocuments() {
        OntologyModel extended = OntologyModel.fromTurtle(TestOntology.ONTOLOGY, TestOntology.PROFILE, """
                @prefix sdam: <https://semanticdam.com/ontology/core#> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix acme: <https://acme.example/ontology#> .
                <https://acme.example/ontology> a owl:Ontology ; owl:versionInfo "acme-1" .
                acme:region a owl:DatatypeProperty ; rdfs:label "Region" .
                acme:RegionMapping a sdam:PropertyMapping ;
                    sdam:sourceProperty "acme:region" ;
                    sdam:sourceSystem <https://semanticdam.com/id/agent/PIM> ; sdam:predicate acme:region .
                """);

        assertEquals("3.0.0+profile-1+acme-1", extended.version());
        assertEquals(12, extended.mappings().size());
        assertTrue(extended.findByLabel("Region").isPresent());
    }

    /** A controlled vocabulary needs definitions: every term of the domain ontology has one. */
    @Test
    void everyTermOfTheDomainOntologyHasADefinition() {
        OntologyModel domain = OntologyModel.fromTurtle(TestOntology.ONTOLOGY);
        List<String> types = List.of(Vocabulary.OWL + "Class", Vocabulary.OWL + "ObjectProperty",
                Vocabulary.OWL + "DatatypeProperty", Vocabulary.CONCEPT_CLASS, Vocabulary.CONCEPT_SCHEME_CLASS,
                Vocabulary.PROV + "SoftwareAgent");
        int checked = 0;
        for (String type : types) {
            for (var term : domain.graph().instancesOf(type)) {
                assertTrue(domain.definition(term.value()).isPresent(), term.value() + " has no skos:definition");
                checked++;
            }
        }
        assertTrue(checked > 25, "checked " + checked);
    }

    @Test
    void separatesTheDomainOntologyFromTheApplicationProfile() {
        OntologyModel domain = OntologyModel.fromTurtle(TestOntology.ONTOLOGY);
        assertTrue(domain.mappings().isEmpty());
        assertTrue(domain.rules().isEmpty());
        assertTrue(domain.shapesFor(List.of(Vocabulary.ASSET)).isEmpty());
        assertTrue(domain.findByUri(Vocabulary.BRAND).isPresent());
    }

    @Test
    void describesSchemesPoliciesAndExpansions() {
        assertEquals("https://semanticdam.com/taxonomy/sustainability#Scheme",
                ontology.findEntity(SUSTAIN + "OrganicCotton").orElseThrow().scheme());
        assertEquals(List.of(Vocabulary.BROADER_SUBJECT), List.copyOf(ontology.expansionsOf(Vocabulary.SUBJECT)));
        assertTrue(ontology.mappings().stream().filter(m -> m.predicateUri().equals(Vocabulary.BRAND))
                .noneMatch(PropertyMapping::createsApprovedEntities));
        assertTrue(ontology.mappings().stream().filter(m -> m.predicateUri().equals(Vocabulary.PART_OF_CAMPAIGN))
                .allMatch(PropertyMapping::createsApprovedEntities));
        assertEquals("PIM", ontology.sourceSystems().stream()
                .filter(system -> system.uri().equals("https://semanticdam.com/id/agent/PIM")).findFirst().orElseThrow().label());
    }

    @Test
    void failsFastOnBrokenDeclarations() {
        assertThrows(IllegalStateException.class, () -> OntologyModel.fromTurtle("""
                @prefix sdam: <https://semanticdam.com/ontology/core#> .
                sdam:Broken a sdam:PropertyMapping ; sdam:sourceProperty "x:y" ; sdam:predicate sdam:p .
                """));
        assertThrows(RuntimeException.class, () -> OntologyModel.fromTurtle("this is not turtle"));
    }
}
