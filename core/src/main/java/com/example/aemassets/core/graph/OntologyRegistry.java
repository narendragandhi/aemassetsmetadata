package com.example.aemassets.core.graph;

import com.example.aemassets.core.domain.OntologyTerm;

import java.util.Optional;

public interface OntologyRegistry {
    Optional<OntologyTerm> findByUri(String uri);

    Optional<OntologyTerm> findByLabel(String label);
}
