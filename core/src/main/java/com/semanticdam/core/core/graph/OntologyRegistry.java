package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.OntologyTerm;
import com.semanticdam.core.core.ontology.InferenceRule;
import com.semanticdam.core.core.ontology.NodeShape;
import com.semanticdam.core.core.ontology.PropertyMapping;
import com.semanticdam.core.core.ontology.SourceSystem;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Read access to the loaded ontology: vocabulary, shapes, mappings, taxonomy and rules. */
public interface OntologyRegistry {
    Optional<OntologyTerm> findByUri(String uri);

    Optional<OntologyTerm> findByLabel(String label);

    /**
     * Whether a predicate may be used on an asset of the named class ("bundle"). A predicate is
     * rejected only when a closed shape applies to the class and does not declare it.
     */
    boolean isAllowedForBundle(String predicateUri, String bundleName);

    /** {@code owl:versionInfo} of the loaded ontology documents. */
    String version();

    Collection<OntologyTerm> terms();

    Collection<SourceSystem> sourceSystems();

    Collection<PropertyMapping> mappings();

    Collection<InferenceRule> rules();

    /** Shapes targeting any of the given classes or their ancestors. */
    Collection<NodeShape> shapesFor(Collection<String> classUris);

    /** Shapes flagged as readiness shapes; these are evaluated explicitly, not by class. */
    Collection<NodeShape> readinessShapes();

    /** The class itself plus every transitive {@code rdfs:subClassOf} ancestor. */
    Set<String> superClasses(String classUri);

    /** Every transitive {@code skos:broader} ancestor of a concept, excluding the concept. */
    Set<String> broaderConcepts(String conceptUri);

    /** An entity declared in the ontology, such as a taxonomy concept. */
    Optional<Entity> findEntity(String uri);

    /** Reconciles a label against entities of a class declared in the ontology. */
    Optional<Entity> findEntityByLabel(String classUri, String label);

    /** The namespace in which new entities of a class are minted. */
    Optional<String> instanceNamespace(String classUri);

    /**
     * Predicates whose values also count as values of the given predicate when searching, e.g.
     * {@code sdam:broaderSubject} for {@code dcterms:subject}. Declared with {@code sdam:expands}.
     */
    default Collection<String> expansionsOf(String predicateUri) {
        return java.util.List.of();
    }
}
