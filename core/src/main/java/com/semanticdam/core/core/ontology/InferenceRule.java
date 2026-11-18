package com.semanticdam.core.core.ontology;

import java.util.regex.Pattern;

/**
 * If a statement has {@code ifPredicate} and either the object {@code ifObject} (a governed entity)
 * or a value or entity label matching {@code ifValuePattern}, assert the consequent.
 */
public record InferenceRule(String uri, String label, String ifPredicate, String ifObject, Pattern ifValuePattern,
        String thenPredicate, String thenValue, boolean thenValueIsUri) {
    public InferenceRule {
        if (ifPredicate == null || (ifObject == null && ifValuePattern == null) || thenPredicate == null
                || thenValue == null) {
            throw new IllegalArgumentException("inference rule " + uri + " is incomplete");
        }
    }
}
