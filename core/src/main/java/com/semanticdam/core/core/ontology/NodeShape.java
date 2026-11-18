package com.semanticdam.core.core.ontology;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The supported subset of a SHACL node shape. */
public record NodeShape(String uri, String label, String targetClass, boolean closed,
        Set<String> ignoredProperties, List<PropertyShape> properties, boolean readiness) {
    public NodeShape {
        ignoredProperties = Set.copyOf(ignoredProperties);
        properties = List.copyOf(properties);
    }

    public Optional<String> target() {
        return Optional.ofNullable(targetClass);
    }

    public boolean declares(String predicateUri) {
        return properties.stream().anyMatch(property -> property.path().equals(predicateUri));
    }
}
