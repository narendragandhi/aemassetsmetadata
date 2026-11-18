package com.semanticdam.core.core.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathOntologySourceTest {
    @Test
    void readsOntologyFromClasspath() {
        ClasspathOntologySource source = new ClasspathOntologySource("/ontology/asset-ontology.ttl");
        String turtle = source.readTurtle();

        assertTrue(turtle.contains("@prefix sdam:"));
        assertTrue(turtle.contains("sdam:Asset a owl:Class"));
    }
}
