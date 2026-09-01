package com.semanticdam.core.core.application;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "SemanticDAM - Qdrant Vector Index")
public @interface QdrantVectorIndexConfig {
    @AttributeDefinition(name = "Qdrant HTTP URL") String baseUrl() default "http://localhost:6333";
    @AttributeDefinition(name = "API key", type = AttributeType.PASSWORD) String apiKey() default "";
    @AttributeDefinition(name = "Collection") String collection() default "semanticdam";
    @AttributeDefinition(name = "Embedding model identifier") String modelId();
    @AttributeDefinition(name = "Embedding model version") String modelVersion() default "1";
    @AttributeDefinition(name = "Request timeout seconds") int timeoutSeconds() default 10;
}
