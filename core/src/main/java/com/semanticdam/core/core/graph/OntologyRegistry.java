package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.OntologyTerm;

import java.util.Optional;

public interface OntologyRegistry {
    Optional<OntologyTerm> findByUri(String uri);

    Optional<OntologyTerm> findByLabel(String label);

    /**
     * Checks if a predicate is allowed for a specific entity bundle (content type).
     * Inspired by Drupal's opinionated Field/Bundle system.
     */
    boolean isAllowedForBundle(String predicateUri, String bundleName);
}
