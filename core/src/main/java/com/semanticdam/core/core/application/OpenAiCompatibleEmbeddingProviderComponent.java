package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.EmbeddingVector;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.Designate;

import java.time.Duration;

/** OSGi wiring; without configuration no embedding provider exists and semantic search stays off. */
@Component(service = EmbeddingProvider.class, configurationPolicy = ConfigurationPolicy.REQUIRE,
        configurationPid = "com.semanticdam.core.core.application.OpenAiCompatibleEmbeddingProviderComponent")
@Designate(ocd = OpenAiCompatibleEmbeddingConfig.class)
public final class OpenAiCompatibleEmbeddingProviderComponent implements EmbeddingProvider {
    private final OpenAiCompatibleEmbeddingProvider delegate;

    @Activate
    public OpenAiCompatibleEmbeddingProviderComponent(OpenAiCompatibleEmbeddingConfig config) {
        this.delegate = new OpenAiCompatibleEmbeddingProvider(config.endpoint(), config.apiKey(), config.modelId(),
                config.modelVersion(), Duration.ofSeconds(Math.max(1, config.timeoutSeconds())));
    }

    @Override
    public EmbeddingVector embed(String input) {
        return delegate.embed(input);
    }
}
