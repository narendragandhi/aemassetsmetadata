package com.semanticdam.core.core.graph;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "AEM Assets Metadata - SPARQL Endpoint")
public @interface SparqlEndpointConfig {
    @AttributeDefinition(name = "Endpoint URL")
    String endpointUrl() default "";

    @AttributeDefinition(name = "Username")
    String username() default "";

    @AttributeDefinition(name = "Password")
    String password() default "";

    @AttributeDefinition(name = "Graph URI")
    String graphUri() default "";
}
