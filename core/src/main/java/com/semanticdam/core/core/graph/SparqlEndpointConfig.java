package com.semanticdam.core.core.graph;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "SemanticDAM - SPARQL Endpoint")
public @interface SparqlEndpointConfig {
    @AttributeDefinition(name = "Query endpoint URL", description = "Empty disables the SPARQL store.")
    String endpointUrl() default "";

    @AttributeDefinition(name = "Update endpoint URL", description = "Defaults to the query endpoint.")
    String updateUrl() default "";

    @AttributeDefinition(name = "Username")
    String username() default "";

    @AttributeDefinition(name = "Password", type = AttributeType.PASSWORD)
    String password() default "";

    @AttributeDefinition(name = "Graph URI", description = "Named graph to use; empty uses the default graph.")
    String graphUri() default "";

    @AttributeDefinition(name = "Request timeout seconds")
    int timeoutSeconds() default 30;
}
