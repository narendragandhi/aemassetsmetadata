package com.semanticdam.core.core.ontology;

import java.util.Optional;

/**
 * Maps one DAM metadata property onto a graph predicate. {@code valueKind} is null for plain values
 * or {@link #AEM_TAG} when the values are AEM tag IDs; {@code instanceNamespace} overrides the
 * entity class's namespace for the IRIs this mapping mints.
 */
public record PropertyMapping(String sourceProperty, SourceSystem sourceSystem, String predicateUri,
        String entityClassUri, String valueKind, String instanceNamespace, boolean createsApprovedEntities) {
    public static final String AEM_TAG = Vocabulary.SDAM + "AemTag";
    public static final String APPROVED_POLICY = Vocabulary.SDAM + "Approved";
    public static final String CANDIDATE_POLICY = Vocabulary.SDAM + "Candidate";

    public PropertyMapping(String sourceProperty, SourceSystem sourceSystem, String predicateUri,
            String entityClassUri) {
        this(sourceProperty, sourceSystem, predicateUri, entityClassUri, null, null, false);
    }

    public PropertyMapping {
        if (sourceProperty == null || sourceProperty.isBlank() || sourceSystem == null
                || predicateUri == null || predicateUri.isBlank()) {
            throw new IllegalArgumentException("sourceProperty, sourceSystem and predicate are required");
        }
    }

    /** The class of entity node this mapping links to, if it is not a plain literal. */
    public Optional<String> entityClass() {
        return Optional.ofNullable(entityClassUri);
    }

    public boolean isAemTag() {
        return AEM_TAG.equals(valueKind);
    }

    public boolean mapsToClass() {
        return Vocabulary.RDF_TYPE.equals(predicateUri);
    }
}
