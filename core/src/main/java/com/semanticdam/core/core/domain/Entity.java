package com.semanticdam.core.core.domain;

import java.util.List;

/**
 * A node other than an asset: a brand, product, campaign, taxonomy concept, AEM tag or concept
 * scheme, as a controlled-vocabulary entry.
 *
 * @param label        preferred label ({@code skos:prefLabel})
 * @param broader      IRIs of broader concepts ({@code skos:broader})
 * @param altLabels    alternative labels: synonyms, translations ({@code skos:altLabel})
 * @param hiddenLabels labels used for matching only: misspellings, merged variants ({@code skos:hiddenLabel})
 * @param scheme       IRI of the concept scheme, or null ({@code skos:inScheme})
 * @param status       vocabulary status; only APPROVED entries make links valid
 * @param replacedBy   for MERGED entries, the entry that replaced this one ({@code dcterms:isReplacedBy})
 */
public record Entity(String uri, String typeUri, String label, List<String> broader, List<String> altLabels,
        List<String> hiddenLabels, String scheme, Status status, String replacedBy) {

    /** Lifecycle of a controlled-vocabulary entry. */
    public enum Status { APPROVED, CANDIDATE, REJECTED, MERGED }

    public Entity {
        if (uri == null || uri.isBlank() || typeUri == null || typeUri.isBlank()
                || label == null || label.isBlank()) {
            throw new IllegalArgumentException("entity uri, type and label must be provided");
        }
        broader = broader == null ? List.of() : List.copyOf(broader);
        altLabels = altLabels == null ? List.of() : List.copyOf(altLabels);
        hiddenLabels = hiddenLabels == null ? List.of() : List.copyOf(hiddenLabels);
        status = status == null ? Status.APPROVED : status;
        if (status == Status.MERGED && (replacedBy == null || replacedBy.isBlank())) {
            throw new IllegalArgumentException("a merged entity must name the entity that replaced it");
        }
    }

    public Entity(String uri, String typeUri, String label) {
        this(uri, typeUri, label, List.of());
    }

    public Entity(String uri, String typeUri, String label, List<String> broader) {
        this(uri, typeUri, label, broader, List.of(), List.of(), null, Status.APPROVED, null);
    }

    public boolean approved() {
        return status == Status.APPROVED;
    }

    public Entity withStatus(Status newStatus, String newReplacedBy) {
        return new Entity(uri, typeUri, label, broader, altLabels, hiddenLabels, scheme, newStatus, newReplacedBy);
    }

    public Entity withHiddenLabel(String hiddenLabel) {
        if (hiddenLabel == null || hiddenLabel.isBlank() || hiddenLabels.contains(hiddenLabel) || label.equals(hiddenLabel)) {
            return this;
        }
        List<String> labels = new java.util.ArrayList<>(hiddenLabels);
        labels.add(hiddenLabel);
        return new Entity(uri, typeUri, label, broader, altLabels, labels, scheme, status, replacedBy);
    }
}
