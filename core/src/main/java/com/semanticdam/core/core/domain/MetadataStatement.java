package com.semanticdam.core.core.domain;

import java.util.Objects;

public final class MetadataStatement {
    private final String predicateUri;
    private final String objectValue;
    private final boolean objectIsUri;
    private final String source;
    private final boolean isValid;
    private final double confidenceScore;

    private MetadataStatement(String predicateUri, String objectValue, boolean objectIsUri, String source, boolean isValid, double confidenceScore) {
        if (predicateUri == null || predicateUri.isBlank()) {
            throw new IllegalArgumentException("predicate uri must be provided");
        }
        if (objectValue == null || objectValue.isBlank()) {
            throw new IllegalArgumentException("object value must be provided");
        }
        this.predicateUri = predicateUri;
        this.objectValue = objectValue;
        this.objectIsUri = objectIsUri;
        this.source = source != null ? source : "unknown";
        this.isValid = isValid;
        this.confidenceScore = confidenceScore;
    }

    public static MetadataStatement uri(String predicateUri, String objectUri) {
        return new MetadataStatement(predicateUri, objectUri, true, "unknown", true, 1.0);
    }

    public static MetadataStatement literal(String predicateUri, String literalValue) {
        return new MetadataStatement(predicateUri, literalValue, false, "unknown", true, 1.0);
    }

    public static MetadataStatement uri(String predicateUri, String objectUri, String source) {
        return new MetadataStatement(predicateUri, objectUri, true, source, true, 1.0);
    }

    public static MetadataStatement literal(String predicateUri, String literalValue, String source) {
        return new MetadataStatement(predicateUri, literalValue, false, source, true, 1.0);
    }

    public static MetadataStatement uri(String predicateUri, String objectUri, String source, boolean isValid) {
        return new MetadataStatement(predicateUri, objectUri, true, source, isValid, 1.0);
    }

    public static MetadataStatement literal(String predicateUri, String literalValue, String source, boolean isValid) {
        return new MetadataStatement(predicateUri, literalValue, false, source, isValid, 1.0);
    }

    public static MetadataStatement literal(String predicateUri, String literalValue, String source, boolean isValid, double confidenceScore) {
        return new MetadataStatement(predicateUri, literalValue, false, source, isValid, confidenceScore);
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

    public String source() {
        return source;
    }

    public boolean isValid() {
        return isValid;
    }

    public double confidenceScore() {
        return confidenceScore;
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
                && isValid == that.isValid
                && Double.compare(that.confidenceScore, confidenceScore) == 0
                && Objects.equals(predicateUri, that.predicateUri)
                && Objects.equals(objectValue, that.objectValue)
                && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(predicateUri, objectValue, objectIsUri, source, isValid, confidenceScore);
    }

    @Override
    public String toString() {
        return "[" + source + " (" + confidenceScore + ")" + (isValid ? "" : " INVALID") + "] " + predicateUri + " -> " + objectValue;
    }
}
