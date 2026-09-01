package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.GroundedAssetContext;

import java.util.Collection;

/** Decorator adding bounded retry, circuit breaking, rate limiting, and telemetry. */
public final class ResilientAiEnrichmentProvider implements AiEnrichmentProvider {
    private final AiEnrichmentProvider delegate;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final ProviderTelemetry telemetry;
    private final int maxAttempts;
    private final double inputCostPerThousand;
    private final double outputCostPerThousand;

    public ResilientAiEnrichmentProvider(AiEnrichmentProvider delegate, RateLimiter rateLimiter,
            CircuitBreaker circuitBreaker, ProviderTelemetry telemetry, int maxAttempts) {
        this(delegate, rateLimiter, circuitBreaker, telemetry, maxAttempts, 0.0, 0.0);
    }

    public ResilientAiEnrichmentProvider(AiEnrichmentProvider delegate, RateLimiter rateLimiter,
            CircuitBreaker circuitBreaker, ProviderTelemetry telemetry, int maxAttempts,
            double inputCostPerThousand, double outputCostPerThousand) {
        if (delegate == null || rateLimiter == null || circuitBreaker == null || telemetry == null || maxAttempts <= 0) {
            throw new IllegalArgumentException("complete resilience configuration must be provided");
        }
        if (inputCostPerThousand < 0.0 || outputCostPerThousand < 0.0) {
            throw new IllegalArgumentException("cost rates cannot be negative");
        }
        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.telemetry = telemetry;
        this.maxAttempts = maxAttempts;
        this.inputCostPerThousand = inputCostPerThousand;
        this.outputCostPerThousand = outputCostPerThousand;
    }

    @Override
    public String providerId() { return delegate.providerId(); }

    @Override
    public Collection<AiEnrichmentProposal> propose(GroundedAssetContext context) {
        if (context == null) throw new IllegalArgumentException("context must be provided");
        if (!rateLimiter.tryAcquire()) throw new IllegalStateException("AI provider rate limit exceeded");
        if (!circuitBreaker.allowRequest()) throw new IllegalStateException("AI provider circuit is open");
        long started = System.nanoTime();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Collection<AiEnrichmentProposal> result = delegate.propose(context);
                circuitBreaker.recordSuccess();
                int input = context.asset().statements().toString().length();
                int output = result.toString().length();
                telemetry.recordSuccess(elapsedMillis(started), input, output);
                telemetry.recordEstimatedCost((input / 1000.0) * inputCostPerThousand
                        + (output / 1000.0) * outputCostPerThousand);
                return result;
            } catch (RuntimeException ex) {
                last = ex;
                circuitBreaker.recordFailure();
            }
        }
        telemetry.recordFailure(elapsedMillis(started));
        throw new IllegalStateException("AI provider failed after " + maxAttempts + " attempts", last);
    }

    private long elapsedMillis(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
