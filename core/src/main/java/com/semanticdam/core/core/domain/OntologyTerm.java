package com.semanticdam.core.core.domain;

import java.util.Objects;
import java.util.Optional;

/** A named term of the ontology: a class, property or taxonomy concept. */
public final class OntologyTerm {
    public enum Kind { CLASS, DATATYPE_PROPERTY, OBJECT_PROPERTY, CONCEPT, OTHER }

    private final String uri;
    private final String label;
    private final String validationPattern;
    private final Kind kind;
    private final String comment;
    private final String range;

    public OntologyTerm(String uri, String label) {
        this(uri, label, null);
    }

    public OntologyTerm(String uri, String label, String validationPattern) {
        this(uri, label, validationPattern, Kind.OTHER, null, null);
    }

    public OntologyTerm(String uri, String label, String validationPattern, Kind kind, String comment,
            String range) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("term uri must be provided");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("term label must be provided");
        }
        this.uri = uri;
        this.label = label;
        this.validationPattern = validationPattern;
        this.kind = kind == null ? Kind.OTHER : kind;
        this.comment = comment;
        this.range = range;
    }

    public String uri() {
        return uri;
    }

    public String label() {
        return label;
    }

    /** The {@code sh:pattern} the asset shape places on this property, if any. */
    public Optional<String> validationPattern() {
        return Optional.ofNullable(validationPattern);
    }

    public Kind kind() {
        return kind;
    }

    public Optional<String> comment() {
        return Optional.ofNullable(comment);
    }

    /** For object properties, the class of node the property points at. */
    public Optional<String> range() {
        return Optional.ofNullable(range);
    }

    public boolean isProperty() {
        return kind == Kind.DATATYPE_PROPERTY || kind == Kind.OBJECT_PROPERTY;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OntologyTerm)) {
            return false;
        }
        OntologyTerm that = (OntologyTerm) other;
        return Objects.equals(uri, that.uri) && Objects.equals(label, that.label)
                && Objects.equals(validationPattern, that.validationPattern) && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, label, validationPattern, kind);
    }

    @Override
    public String toString() {
        return label + " <" + uri + ">" + (validationPattern != null ? " [pattern: " + validationPattern + "]" : "");
    }
}
