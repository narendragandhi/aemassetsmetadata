package com.semanticdam.core.core.ontology;

/** One SHACL validation result. {@code value} is {@code null} for cardinality violations. */
public record Violation(String shapeUri, String path, String value, String constraint, String message) {
}
