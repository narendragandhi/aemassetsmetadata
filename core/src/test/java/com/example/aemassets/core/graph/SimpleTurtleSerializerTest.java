package com.example.aemassets.core.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
