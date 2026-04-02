package com.semanticdam.core.core.domain;

import java.util.Objects;
import java.util.Optional;

public final class OntologyTerm {
    private final String uri;
    private final String label;
    private final String validationPattern;

    public OntologyTerm(String uri, String label) {
        this(uri, label, null);
    }

    public OntologyTerm(String uri, String label, String validationPattern) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("term uri must be provided");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("term label must be provided");
        }
        this.uri = uri;
        this.label = label;
        this.validationPattern = validationPattern;
    }

    public String uri() {
        return uri;
    }

    public String label() {
        return label;
    }

    public Optional<String> validationPattern() {
        return Optional.ofNullable(validationPattern);
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
                && Objects.equals(validationPattern, that.validationPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, label, validationPattern);
    }

    @Override
    public String toString() {
        return label + " <" + uri + ">" + (validationPattern != null ? " [pattern: " + validationPattern + "]" : "");
    }
}
