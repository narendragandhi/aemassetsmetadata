package com.semanticdam.core.core.ontology;

import java.util.List;

/** The supported subset of a SHACL property shape. Unset constraints are {@code null}. */
public record PropertyShape(String path, String datatype, String pattern, String flags, Integer minLength,
        Integer maxLength, Integer minCount, Integer maxCount, String nodeKind, String classUri,
        List<String> in, String message) {
    public PropertyShape {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("sh:path must be an IRI");
        }
        in = in == null ? null : List.copyOf(in);
    }
}
