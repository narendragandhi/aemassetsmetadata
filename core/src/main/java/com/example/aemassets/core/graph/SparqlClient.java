package com.example.aemassets.core.graph;

import java.util.Optional;

public interface SparqlClient {
    void update(String sparqlUpdate);

    Optional<String> query(String sparqlQuery);
}
