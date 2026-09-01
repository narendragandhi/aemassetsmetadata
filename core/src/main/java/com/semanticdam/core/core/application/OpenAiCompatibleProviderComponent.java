package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.GroundedAssetContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.Designate;

import java.time.Duration;
import java.util.Collection;

/** OSGi wiring; disabled or absent configuration leaves no live provider active. */
@Component(service = AiEnrichmentProvider.class, configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {"service.ranking:Integer=100"})
@Designate(ocd = OpenAiCompatibleProviderConfig.class)
public final class OpenAiCompatibleProviderComponent implements AiEnrichmentProvider {
    private volatile AiEnrichmentProvider delegate;

    @org.osgi.service.component.annotations.Activate
    void activate(OpenAiCompatibleProviderConfig config) {
        if (!config.enabled()) return;
        if (config.timeoutSeconds() <= 0 || config.maxAttempts() <= 0 || config.rateLimitPermits() <= 0
                || config.rateLimitWindowSeconds() <= 0 || config.circuitFailureThreshold() <= 0
                || config.circuitOpenSeconds() <= 0) {
            throw new IllegalArgumentException("provider resilience settings must be positive");
        }
        OpenAiCompatibleEnrichmentProvider base = new OpenAiCompatibleEnrichmentProvider(config.endpoint(), config.apiKey(), config.modelId(),
                config.promptVersion(), Duration.ofSeconds(config.timeoutSeconds()),
                new AiEnrichmentPolicy(config.maxProposals(), config.minimumConfidence(),
                        java.util.List.of(config.allowedPredicates())));
        delegate = new ResilientAiEnrichmentProvider(base,
                new TokenBucketRateLimiter(config.rateLimitPermits(), Duration.ofSeconds(config.rateLimitWindowSeconds())),
                new SimpleCircuitBreaker(config.circuitFailureThreshold(), Duration.ofSeconds(config.circuitOpenSeconds())),
                new InMemoryProviderTelemetry(), config.maxAttempts(), config.inputCostPerThousand(),
                config.outputCostPerThousand());
    }

    @Override
    public String providerId() {
        return "openai-compatible";
    }

    @Override
    public Collection<AiEnrichmentProposal> propose(GroundedAssetContext context) {
        AiEnrichmentProvider provider = delegate;
        if (provider == null) throw new IllegalStateException("OpenAI-compatible provider is disabled");
        return provider.propose(context);
    }
}
