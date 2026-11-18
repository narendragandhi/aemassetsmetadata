package com.semanticdam.core.core.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleTurtleSerializerTest {
    @Test
    void serializesTriplesToTurtle() {
        GraphTriple triple = new GraphTriple(
                "https://example.com/asset/1",
                "http://purl.org/dc/terms/title",
                "Hero",
                false
        );

        SimpleTurtleSerializer serializer = new SimpleTurtleSerializer();
        String turtle = serializer.toTurtle(List.of(triple));

        assertTrue(turtle.contains("<https://example.com/asset/1>"));
        assertTrue(turtle.contains("<http://purl.org/dc/terms/title>"));
        assertTrue(turtle.contains("\"Hero\""));
    }

    /** Output must survive a round trip through a Turtle parser whatever the values contain. */
    @Test
    void escapesLiteralsAndIrisSoThatOutputStaysParseable() {
        String nasty = "line one\nline \"two\" \\ tab\there <tag> .";
        List<GraphTriple> triples = List.of(
                new GraphTriple("https://semanticdam.com/id/asset/content/dam/my folder/a \"b\".jpg",
                        "http://purl.org/dc/terms/title", nasty, false),
                new GraphTriple("https://semanticdam.com/id/asset/content/dam/x.jpg",
                        "https://schema.org/brand", "https://semanticdam.com/id/brand/acme", true));

        com.semanticdam.core.core.rdf.RdfGraph parsed =
                com.semanticdam.core.core.rdf.TurtleParser.parse(new SimpleTurtleSerializer().toTurtle(triples));

        assertEquals(2, parsed.size());
        assertEquals(nasty, parsed.string(com.semanticdam.core.core.rdf.RdfTerm.iri(
                "https://semanticdam.com/id/asset/content/dam/my%20folder/a%20%22b%22.jpg"),
                "http://purl.org/dc/terms/title").orElseThrow());
    }
}
