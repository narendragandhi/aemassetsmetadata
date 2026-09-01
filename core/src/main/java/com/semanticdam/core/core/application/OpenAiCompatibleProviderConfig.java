package com.semanticdam.core.core.application;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "SemanticDAM - OpenAI-Compatible Enrichment Provider")
public @interface OpenAiCompatibleProviderConfig {
    @AttributeDefinition(name = "Enabled") boolean enabled() default false;
    @AttributeDefinition(name = "Chat completions endpoint") String endpoint() default "";
    @AttributeDefinition(name = "Bearer token", type = AttributeType.PASSWORD) String apiKey() default "";
    @AttributeDefinition(name = "Model identifier") String modelId() default "";
    @AttributeDefinition(name = "Prompt version") String promptVersion() default "v1";
    @AttributeDefinition(name = "Request timeout seconds") int timeoutSeconds() default 20;
    @AttributeDefinition(name = "Maximum proposals") int maxProposals() default 5;
    @AttributeDefinition(name = "Minimum confidence") double minimumConfidence() default 0.8;
    @AttributeDefinition(name = "Allowed canonical predicates") String[] allowedPredicates() default {};
    @AttributeDefinition(name = "Maximum attempts") int maxAttempts() default 3;
    @AttributeDefinition(name = "Rate-limit permits") int rateLimitPermits() default 30;
    @AttributeDefinition(name = "Rate-limit window seconds") int rateLimitWindowSeconds() default 60;
    @AttributeDefinition(name = "Circuit failure threshold") int circuitFailureThreshold() default 5;
    @AttributeDefinition(name = "Circuit open seconds") int circuitOpenSeconds() default 60;
    @AttributeDefinition(name = "Estimated input cost per 1,000 characters") double inputCostPerThousand() default 0.0;
    @AttributeDefinition(name = "Estimated output cost per 1,000 characters") double outputCostPerThousand() default 0.0;
}
