package com.example.aemassets.core.graph;

import java.util.Objects;

public final class GraphTriple {
    private final String subject;
    private final String predicate;
    private final String object;
    private final boolean objectIsUri;

    public GraphTriple(String subject, String predicate, String object, boolean objectIsUri) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must be provided");
        }
        if (predicate == null || predicate.isBlank()) {
            throw new IllegalArgumentException("predicate must be provided");
        }
        if (object == null || object.isBlank()) {
            throw new IllegalArgumentException("object must be provided");
        }
        this.subject = subject;
        this.predicate = predicate;
        this.object = object;
        this.objectIsUri = objectIsUri;
    }

    public String subject() {
        return subject;
    }

    public String predicate() {
        return predicate;
    }

    public String object() {
        return object;
    }

    public boolean objectIsUri() {
        return objectIsUri;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphTriple)) {
            return false;
        }
        GraphTriple that = (GraphTriple) other;
        return objectIsUri == that.objectIsUri
                && Objects.equals(subject, that.subject)
                && Objects.equals(predicate, that.predicate)
                && Objects.equals(object, that.object);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, predicate, object, objectIsUri);
    }

    @Override
    public String toString() {
        return subject + " " + predicate + " " + object + ".";
    }
}
