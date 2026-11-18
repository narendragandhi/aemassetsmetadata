package com.semanticdam.core.core.application;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "SemanticDAM - OpenAI-Compatible Embedding Provider")
public @interface OpenAiCompatibleEmbeddingConfig {
    @AttributeDefinition(name = "Embeddings endpoint", description = "For example https://api.openai.com/v1/embeddings "
            + "or http://localhost:11434/v1/embeddings for Ollama.")
    String endpoint();

    @AttributeDefinition(name = "Bearer token", type = AttributeType.PASSWORD)
    String apiKey() default "";

    @AttributeDefinition(name = "Model identifier", description = "Must equal the model id of the vector index.")
    String modelId();

    @AttributeDefinition(name = "Model version", description = "Must equal the model version of the vector index. "
            + "Change both and reindex when the model changes.")
    String modelVersion() default "1";

    @AttributeDefinition(name = "Request timeout seconds")
    int timeoutSeconds() default 20;
}
