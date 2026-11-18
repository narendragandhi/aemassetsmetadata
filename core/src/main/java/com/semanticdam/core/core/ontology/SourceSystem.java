package com.semanticdam.core.core.ontology;

/** A system that contributes metadata, with the confidence assigned to its statements. */
public record SourceSystem(String uri, String label, double trust) {
    public SourceSystem {
        if (label == null || label.isBlank() || trust < 0.0 || trust > 1.0) {
            throw new IllegalArgumentException("source system needs a label and a trust between 0 and 1");
        }
    }
}
