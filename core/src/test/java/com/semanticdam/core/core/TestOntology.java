package com.semanticdam.core.core;

import com.semanticdam.core.core.graph.ClasspathOntologySource;
import com.semanticdam.core.core.ontology.OntologyModel;

/** The bundled ontology and default application profile, loaded as the OSGi component loads them. */
public final class TestOntology {
    public static final String ONTOLOGY = new ClasspathOntologySource("/ontology/asset-ontology.ttl").readTurtle();
    public static final String PROFILE = new ClasspathOntologySource("/ontology/application-profile.ttl").readTurtle();
    /** Both documents concatenated, for tools that take one Turtle document (Jena SHACL). */
    public static final String TURTLE = ONTOLOGY + "\n" + PROFILE;

    private TestOntology() {
    }

    public static OntologyModel load() {
        return OntologyModel.fromTurtle(ONTOLOGY, PROFILE);
    }
}
