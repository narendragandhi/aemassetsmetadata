package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;

public class SparqlQueryBuilder {
    private final GraphSubjectResolver subjectResolver;

    public SparqlQueryBuilder() {
        this(new GraphSubjectResolver());
    }

    public SparqlQueryBuilder(GraphSubjectResolver subjectResolver) {
        if (subjectResolver == null) {
            throw new IllegalArgumentException("subjectResolver must be provided");
        }
        this.subjectResolver = subjectResolver;
    }

    public String selectAssetTriples(AssetId assetId, String graphUri) {
        String subject = "<" + subjectResolver.assetSubject(assetId) + ">";
        String graphClause = graphUri == null || graphUri.isBlank()
                ? ""
                : "GRAPH <" + graphUri + "> ";

        return "SELECT ?p ?o WHERE { " + graphClause + subject + " ?p ?o . }";
    }
}
