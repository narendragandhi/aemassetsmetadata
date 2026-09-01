package com.semanticdam.core.core.domain;

import java.util.Arrays;

/**
 * An immutable embedding with the identity needed for safe re-indexing.
 */
public final class EmbeddingVector {
    private final double[] values;
    private final String modelId;
    private final String modelVersion;

    private EmbeddingVector(String modelId, String modelVersion, double[] values) {
        if (modelId == null || modelId.isBlank() || modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalArgumentException("embedding model id and version must be provided");
        }
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("embedding must contain at least one value");
        }
        this.modelId = modelId;
        this.modelVersion = modelVersion;
        this.values = Arrays.copyOf(values, values.length);
        for (double value : this.values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("embedding values must be finite");
            }
        }
    }

    public static EmbeddingVector of(double... values) {
        return new EmbeddingVector("unspecified", "unspecified", values);
    }

    public static EmbeddingVector of(String modelId, String modelVersion, double... values) {
        return new EmbeddingVector(modelId, modelVersion, values);
    }

    public String modelId() {
        return modelId;
    }

    public String modelVersion() {
        return modelVersion;
    }

    public int dimension() {
        return values.length;
    }

    public double valueAt(int index) {
        return values[index];
    }

    public double[] values() {
        return Arrays.copyOf(values, values.length);
    }
}
