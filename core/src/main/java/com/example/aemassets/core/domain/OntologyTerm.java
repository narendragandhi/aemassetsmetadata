package com.example.aemassets.core.domain;

import java.util.Objects;

public final class OntologyTerm {
    private final String uri;
    private final String label;

    public OntologyTerm(String uri, String label) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("term uri must be provided");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("term label must be provided");
        }
        this.uri = uri;
        this.label = label;
    }

    public String uri() {
        return uri;
    }

    public String label() {
        return label;
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
        return Objects.equals(uri, that.uri) && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, label);
    }

    @Override
    public String toString() {
        return label + " <" + uri + ">";
    }
}
