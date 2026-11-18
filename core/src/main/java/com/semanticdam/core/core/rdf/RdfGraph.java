package com.semanticdam.core.core.rdf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** An immutable, indexed set of triples with the lookups the ontology layer needs. */
public final class RdfGraph {
    public static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String RDF_TYPE = RDF + "type";
    public static final String RDF_FIRST = RDF + "first";
    public static final String RDF_REST = RDF + "rest";
    public static final String RDF_NIL = RDF + "nil";

    private final Set<RdfTriple> triples;
    private final Map<RdfTerm, List<RdfTriple>> bySubject = new HashMap<>();
    private final Map<String, List<RdfTriple>> byPredicate = new HashMap<>();

    public RdfGraph(Collection<RdfTriple> triples) {
        this.triples = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(triples));
        for (RdfTriple triple : this.triples) {
            bySubject.computeIfAbsent(triple.subject(), key -> new ArrayList<>()).add(triple);
            byPredicate.computeIfAbsent(triple.predicate().value(), key -> new ArrayList<>()).add(triple);
        }
    }

    public Set<RdfTriple> triples() {
        return triples;
    }

    public int size() {
        return triples.size();
    }

    public List<RdfTerm> objects(RdfTerm subject, String predicateIri) {
        List<RdfTerm> result = new ArrayList<>();
        for (RdfTriple triple : bySubject.getOrDefault(subject, List.of())) {
            if (triple.predicate().value().equals(predicateIri)) {
                result.add(triple.object());
            }
        }
        return result;
    }

    public Optional<RdfTerm> object(RdfTerm subject, String predicateIri) {
        return objects(subject, predicateIri).stream().findFirst();
    }

    public Optional<String> string(RdfTerm subject, String predicateIri) {
        return object(subject, predicateIri).map(RdfTerm::value);
    }

    public List<RdfTerm> subjects(String predicateIri, RdfTerm object) {
        List<RdfTerm> result = new ArrayList<>();
        for (RdfTriple triple : byPredicate.getOrDefault(predicateIri, List.of())) {
            if (triple.object().equals(object)) {
                result.add(triple.subject());
            }
        }
        return result;
    }

    /** Subjects declared with {@code rdf:type typeIri}, in document order. */
    public List<RdfTerm> instancesOf(String typeIri) {
        return subjects(RDF_TYPE, RdfTerm.iri(typeIri));
    }

    public List<RdfTriple> withPredicate(String predicateIri) {
        return List.copyOf(byPredicate.getOrDefault(predicateIri, List.of()));
    }

    /** Reads an RDF collection ({@code ( a b c )}) starting at its head node. */
    public List<RdfTerm> list(RdfTerm head) {
        List<RdfTerm> items = new ArrayList<>();
        RdfTerm current = head;
        Set<RdfTerm> seen = new LinkedHashSet<>();
        while (current != null && !(current.isIri() && RDF_NIL.equals(current.value())) && seen.add(current)) {
            object(current, RDF_FIRST).ifPresent(items::add);
            current = object(current, RDF_REST).orElse(null);
        }
        return items;
    }
}
