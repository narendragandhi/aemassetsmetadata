package com.example.aemassets.core.graph;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ClasspathOntologySource implements OntologySource {
    private final String resourcePath;

    public ClasspathOntologySource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("resourcePath must be provided");
        }
        this.resourcePath = resourcePath;
    }

    @Override
    public String readTurtle() {
        try (InputStream input = getClass().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Ontology resource not found: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read ontology resource: " + resourcePath, ex);
        }
    }
}
