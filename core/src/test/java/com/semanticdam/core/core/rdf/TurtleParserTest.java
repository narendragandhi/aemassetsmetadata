package com.semanticdam.core.core.rdf;

import com.semanticdam.core.core.graph.ClasspathOntologySource;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurtleParserTest {
    private static final String EX = "http://example.org/";

    @Test
    void parsesPredicateAndObjectLists() {
        RdfGraph graph = TurtleParser.parse("""
                @prefix ex: <http://example.org/> .
                ex:a a ex:Thing ;
                    ex:name "A" , "Alpha"@en ;
                    ex:knows ex:b .
                """);

        assertEquals(4, graph.size());
        assertEquals(List.of(RdfTerm.iri(EX + "Thing")), graph.objects(RdfTerm.iri(EX + "a"), RdfGraph.RDF_TYPE));
        assertEquals(List.of(RdfTerm.literal("A"), RdfTerm.langLiteral("Alpha", "en")),
                graph.objects(RdfTerm.iri(EX + "a"), EX + "name"));
    }

    @Test
    void parsesLiteralFormsAndEscapes() {
        RdfGraph graph = TurtleParser.parse("""
                PREFIX ex: <http://example.org/>
                PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                ex:a ex:p "tab\\there \\"quoted\\" \\u00e9" ;
                     ex:q '''multi
                line''' ;
                     ex:n 42 ; ex:d 0.95 ; ex:e 1.5e3 ; ex:b true ;
                     ex:t "2026-01-01"^^xsd:date .
                """);
        RdfTerm a = RdfTerm.iri(EX + "a");

        assertEquals("tab\there \"quoted\" é", graph.string(a, EX + "p").orElseThrow());
        assertEquals("multi\nline", graph.string(a, EX + "q").orElseThrow());
        assertEquals(RdfTerm.XSD + "integer", graph.object(a, EX + "n").orElseThrow().datatype());
        assertEquals(RdfTerm.XSD + "decimal", graph.object(a, EX + "d").orElseThrow().datatype());
        assertEquals(RdfTerm.XSD + "double", graph.object(a, EX + "e").orElseThrow().datatype());
        assertEquals(RdfTerm.XSD + "boolean", graph.object(a, EX + "b").orElseThrow().datatype());
        assertEquals(RdfTerm.XSD + "date", graph.object(a, EX + "t").orElseThrow().datatype());
    }

    @Test
    void parsesBlankNodePropertyListsAndCollections() {
        RdfGraph graph = TurtleParser.parse("""
                @prefix ex: <http://example.org/> .
                ex:shape ex:property [ ex:path ex:sku ; ex:in ( "a" "b" "c" ) ] .
                """);
        RdfTerm property = graph.object(RdfTerm.iri(EX + "shape"), EX + "property").orElseThrow();

        assertTrue(property.isBlank());
        assertEquals(EX + "sku", graph.string(property, EX + "path").orElseThrow());
        assertEquals(List.of("a", "b", "c"), graph.list(graph.object(property, EX + "in").orElseThrow())
                .stream().map(RdfTerm::value).toList());
    }

    @Test
    void doesNotTreatHashInsideIriOrLiteralAsComment() {
        RdfGraph graph = TurtleParser.parse("""
                # leading comment
                <http://example.org/ns#a> <http://example.org/ns#p> "value # not a comment" . # trailing
                """);

        assertEquals("value # not a comment",
                graph.string(RdfTerm.iri("http://example.org/ns#a"), "http://example.org/ns#p").orElseThrow());
    }

    @Test
    void rejectsMalformedDocumentsWithPosition() {
        // The previous ontology file used this form: a missing 'a' keyword.
        TurtleParseException missingVerb = assertThrows(TurtleParseException.class, () -> TurtleParser.parse("""
                @prefix ex: <http://example.org/> .
                ex:Asset ex:Class ;
                    ex:label "Asset" .
                """));
        assertTrue(missingVerb.getMessage().contains("line 2"), missingVerb.getMessage());

        assertThrows(TurtleParseException.class, () -> TurtleParser.parse("ex:a ex:b ex:c ."));
        assertThrows(TurtleParseException.class, () -> TurtleParser.parse("<http://e/a> <http://e/b> \"open ."));
        assertThrows(TurtleParseException.class, () -> TurtleParser.parse("<http://e/a> <http://e/b> <http://e/c>"));
    }

    /** The bundled ontology must mean exactly what a reference RDF implementation says it means. */
    @Test
    void agreesWithJenaOnTheBundledOntology() {
        for (String file : List.of("/ontology/asset-ontology.ttl", "/ontology/application-profile.ttl")) {
            assertSameAsJena(new ClasspathOntologySource(file).readTurtle());
        }
    }

    private static void assertSameAsJena(String turtle) {

        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE);
        Set<String> expected = new TreeSet<>();
        model.listStatements().forEach(statement -> expected.add(canonical(statement)));

        Set<String> actual = new TreeSet<>();
        for (RdfTriple triple : TurtleParser.parse(turtle).triples()) {
            actual.add(canonical(triple.subject()) + " " + triple.predicate().value() + " " + canonical(triple.object()));
        }

        assertTrue(expected.size() > 100, "document unexpectedly small: " + expected.size());
        assertEquals(expected, actual);
    }

    // Blank node labels differ between parsers, so they are compared by position only.
    private static String canonical(Statement statement) {
        return canonical(statement.getSubject()) + " " + statement.getPredicate().getURI() + " "
                + canonical(statement.getObject());
    }

    private static String canonical(RDFNode node) {
        if (node.isAnon()) {
            return "_:b";
        }
        if (node.isURIResource()) {
            return node.asResource().getURI();
        }
        return "\"" + node.asLiteral().getLexicalForm() + "\"^^" + node.asLiteral().getDatatypeURI()
                + "@" + node.asLiteral().getLanguage();
    }

    private static String canonical(RdfTerm term) {
        if (term.isBlank()) {
            return "_:b";
        }
        if (term.isIri()) {
            return term.value();
        }
        return "\"" + term.value() + "\"^^" + term.datatype() + "@" + (term.language() == null ? "" : term.language());
    }
}
