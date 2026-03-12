package com.example.aemassets.core.domain;

import java.util.Objects;

public final class MetadataStatement {
    private final String predicateUri;
    private final String objectValue;
    private final boolean objectIsUri;

    private MetadataStatement(String predicateUri, String objectValue, boolean objectIsUri) {
        if (predicateUri == null || predicateUri.isBlank()) {
            throw new IllegalArgumentException("predicate uri must be provided");
        }
        if (objectValue == null || objectValue.isBlank()) {
            throw new IllegalArgumentException("object value must be provided");
        }
        this.predicateUri = predicateUri;
        this.objectValue = objectValue;
        this.objectIsUri = objectIsUri;
    }

    public static MetadataStatement uri(String predicateUri, String objectUri) {
        return new MetadataStatement(predicateUri, objectUri, true);
    }

    public static MetadataStatement literal(String predicateUri, String literalValue) {
        return new MetadataStatement(predicateUri, literalValue, false);
    }

    public String predicateUri() {
        return predicateUri;
    }

    public String objectValue() {
        return objectValue;
    }

    public boolean objectIsUri() {
        return objectIsUri;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MetadataStatement)) {
            return false;
        }
        MetadataStatement that = (MetadataStatement) other;
        return objectIsUri == that.objectIsUri
                && Objects.equals(predicateUri, that.predicateUri)
                && Objects.equals(objectValue, that.objectValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(predicateUri, objectValue, objectIsUri);
    }

    @Override
    public String toString() {
        return predicateUri + " -> " + objectValue;
    }
}
