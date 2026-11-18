package com.semanticdam.core.core.ontology;

import com.semanticdam.core.core.TestOntology;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaclValidatorTest {
    private static final String PRODUCT_IMAGE = Vocabulary.SDAM + "ProductImage";
    private static final String WF_PROJECT = "https://semanticdam.com/ontology/workfront#projectId";
    private static final String PRIORITY = "https://semanticdam.com/ontology/strategy#priority";
    private static final Entity ACME = new Entity("https://semanticdam.com/id/brand/acme", Vocabulary.BRAND_CLASS, "Acme");
    private static final Entity SUMMER = new Entity("https://semanticdam.com/id/campaign/summer",
            Vocabulary.CAMPAIGN_CLASS, "Summer");
    private static final Map<String, Entity> ENTITIES = Map.of(ACME.uri(), ACME, SUMMER.uri(), SUMMER);

    private final OntologyModel ontology = TestOntology.load();
    private final com.semanticdam.core.core.ontology.ShaclValidator validator =
            new com.semanticdam.core.core.ontology.ShaclValidator(
                    uri -> Optional.ofNullable(ENTITIES.get(uri)), ontology::superClasses);

    @Test
    void acceptsAConformingAsset() {
        List<MetadataStatement> statements = List.of(
                MetadataStatement.literal(Vocabulary.TITLE, "Hero"),
                MetadataStatement.literal(Vocabulary.SKU, "SUM-2026"),
                MetadataStatement.uri(Vocabulary.BRAND, ACME.uri()),
                MetadataStatement.literal(PRIORITY, "Tier-1-Global"));

        assertTrue(validator.validateNode(assetShapes(), statements).isEmpty());
    }

    @Test
    void reportsPatternNodeKindClassInAndCardinalityViolations() {
        List<MetadataStatement> statements = List.of(
                MetadataStatement.literal(Vocabulary.SKU, "bad sku"),
                MetadataStatement.literal(Vocabulary.TITLE, "One"),
                MetadataStatement.literal(Vocabulary.TITLE, "Two"),
                MetadataStatement.literal(Vocabulary.BRAND, "Acme"),
                MetadataStatement.uri(Vocabulary.PART_OF_CAMPAIGN, ACME.uri()),
                MetadataStatement.literal(PRIORITY, "Urgent"));

        Set<String> found = validator.validateNode(assetShapes(), statements).stream()
                .map(violation -> violation.path() + " " + violation.constraint())
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(new TreeSet<>(Set.of(
                Vocabulary.SKU + " sh:pattern",
                Vocabulary.TITLE + " sh:maxCount",
                Vocabulary.BRAND + " sh:nodeKind",
                Vocabulary.BRAND + " sh:class",
                Vocabulary.PART_OF_CAMPAIGN + " sh:class",
                PRIORITY + " sh:in")), found);
    }

    @Test
    void usesTheShapeMessageWhenOneIsGiven() {
        Violation violation = validator.validateStatement(assetShapes(),
                MetadataStatement.literal(Vocabulary.SKU, "bad sku")).get(0);

        assertEquals("SKU must be upper-case letters, digits and hyphens.", violation.message());
        assertEquals("bad sku", violation.value());
    }

    @Test
    void closedShapeRejectsUndeclaredPredicatesButIgnoresType() {
        Collection<NodeShape> shapes = ontology.shapesFor(List.of(PRODUCT_IMAGE));

        assertTrue(validator.validateStatement(shapes,
                MetadataStatement.uri(Vocabulary.RDF_TYPE, PRODUCT_IMAGE)).isEmpty());
        assertEquals("sh:closed", validator.validateStatement(shapes,
                MetadataStatement.literal("https://example.com/internalNote", "x")).get(0).constraint());
    }

    @Test
    void closedShapeRequiresItsMandatoryProperties() {
        Collection<NodeShape> shapes = ontology.shapesFor(List.of(PRODUCT_IMAGE));
        List<MetadataStatement> onlyTitle = List.of(MetadataStatement.literal(Vocabulary.TITLE, "Hero"));

        List<Violation> violations = validator.validateNode(shapes, onlyTitle);

        assertEquals(1, violations.size());
        assertEquals(Vocabulary.SKU, violations.get(0).path());
        assertEquals("sh:minCount", violations.get(0).constraint());
    }

    @Test
    void checksLexicalFormsOfDatatypes() {
        NodeShape shape = new NodeShape("urn:shape", "Typed", null, false, Set.of(), List.of(
                property("urn:int", RdfDatatypes.INTEGER), property("urn:date", RdfDatatypes.DATE),
                property("urn:bool", RdfDatatypes.BOOLEAN)), false);

        assertTrue(validator.validateNode(List.of(shape), List.of(
                MetadataStatement.literal("urn:int", "42"), MetadataStatement.literal("urn:date", "2026-11-16"),
                MetadataStatement.literal("urn:bool", "true"))).isEmpty());
        assertEquals(3, validator.validateNode(List.of(shape), List.of(
                MetadataStatement.literal("urn:int", "4.2"), MetadataStatement.literal("urn:date", "16/11/2026"),
                MetadataStatement.literal("urn:bool", "yes"))).size());
    }

    /**
     * The same data, validated by Apache Jena against the same shapes, must fail on the same
     * (path, constraint) pairs. This guards the hand-written validator against drifting from SHACL.
     */
    @Test
    void agreesWithJenaShacl() {
        String asset = "https://semanticdam.com/id/asset/content/dam/a.jpg";
        String data = """
                @prefix sdam:    <https://semanticdam.com/ontology/core#> .
                @prefix schema:  <https://schema.org/> .
                @prefix dcterms: <http://purl.org/dc/terms/> .
                @prefix skos:    <http://www.w3.org/2004/02/skos/core#> .
                @prefix wf:      <https://semanticdam.com/ontology/workfront#> .
                @prefix strategy: <https://semanticdam.com/ontology/strategy#> .
                <%s> a sdam:ProductImage ;
                    dcterms:title "One" , "Two" ;
                    schema:sku "bad sku" ;
                    schema:brand <https://semanticdam.com/id/brand/acme> ;
                    sdam:partOfCampaign <https://semanticdam.com/id/brand/acme> ;
                    wf:projectId "0123456789abcdef0123456789abcdef" ;
                    strategy:priority "Urgent" ;
                    <https://example.com/internalNote> "x" .
                <https://semanticdam.com/id/brand/acme> a schema:Brand ; skos:prefLabel "Acme" .
                """.formatted(asset);
        List<MetadataStatement> statements = List.of(
                MetadataStatement.uri(Vocabulary.RDF_TYPE, PRODUCT_IMAGE),
                MetadataStatement.literal(Vocabulary.TITLE, "One"),
                MetadataStatement.literal(Vocabulary.TITLE, "Two"),
                MetadataStatement.literal(Vocabulary.SKU, "bad sku"),
                MetadataStatement.uri(Vocabulary.BRAND, ACME.uri()),
                MetadataStatement.uri(Vocabulary.PART_OF_CAMPAIGN, ACME.uri()),
                MetadataStatement.literal(WF_PROJECT, "0123456789abcdef0123456789abcdef"),
                MetadataStatement.literal(PRIORITY, "Urgent"),
                MetadataStatement.literal("https://example.com/internalNote", "x"));

        Set<String> ours = validator.validateNode(ontology.shapesFor(List.of(PRODUCT_IMAGE)), statements).stream()
                .map(violation -> violation.path() + " " + violation.constraint().substring(3))
                .collect(Collectors.toCollection(TreeSet::new));

        Model shapesModel = read(TestOntology.TURTLE);
        // rdfs:subClassOf is only followed by SHACL if it is present in the data graph
        Model dataModel = read(data).add(shapesModel.listStatements(null,
                org.apache.jena.vocabulary.RDFS.subClassOf, (org.apache.jena.rdf.model.RDFNode) null));
        Graph dataGraph = dataModel.getGraph();
        ValidationReport report = ShaclValidator.get().validate(Shapes.parse(shapesModel.getGraph()), dataGraph);
        Set<String> jena = new TreeSet<>();
        report.getEntries().forEach(entry -> {
            if (entry.focusNode().isURI() && entry.focusNode().getURI().equals(asset)) {
                String component = entry.sourceConstraintComponent().getLocalName();
                String constraint = Character.toLowerCase(component.charAt(0))
                        + component.substring(1).replace("ConstraintComponent", "");
                String path = entry.resultPath() == null ? "" : entry.resultPath().toString().replaceAll("[<>]", "");
                jena.add(path + " " + constraint);
            }
        });

        assertFalse(jena.isEmpty());
        assertEquals(jena, ours);
        Resource typed = dataModel.getResource(asset);
        assertTrue(typed.hasProperty(RDF.type));
    }

    private Collection<NodeShape> assetShapes() {
        return ontology.shapesFor(List.of(Vocabulary.ASSET));
    }

    private static PropertyShape property(String path, String datatype) {
        return new PropertyShape(path, datatype, null, null, null, null, null, null, null, null, null, null);
    }

    private static Model read(String turtle) {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE);
        return model;
    }

    private static final class RdfDatatypes {
        static final String INTEGER = "http://www.w3.org/2001/XMLSchema#integer";
        static final String DATE = "http://www.w3.org/2001/XMLSchema#date";
        static final String BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";
    }
}
