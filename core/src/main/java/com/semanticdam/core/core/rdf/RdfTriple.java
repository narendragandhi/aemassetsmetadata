package com.semanticdam.core.core.rdf;

/** One RDF statement. */
public record RdfTriple(RdfTerm subject, RdfTerm predicate, RdfTerm object) {
    public RdfTriple {
        if (subject == null || predicate == null || object == null) {
            throw new IllegalArgumentException("subject, predicate and object must be provided");
        }
        if (subject.isLiteral() || !predicate.isIri()) {
            throw new IllegalArgumentException("invalid triple: " + subject + " " + predicate + " " + object);
        }
    }
}
