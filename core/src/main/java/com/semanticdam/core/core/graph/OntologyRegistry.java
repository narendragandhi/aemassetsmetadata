package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.OntologyTerm;

import java.util.Optional;

public interface OntologyRegistry {
    Optional<OntologyTerm> findByUri(String uri);

    Optional<OntologyTerm> findByLabel(String label);
}
