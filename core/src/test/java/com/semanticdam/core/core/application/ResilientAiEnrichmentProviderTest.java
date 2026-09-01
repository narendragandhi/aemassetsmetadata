package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.GroundedAssetContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResilientAiEnrichmentProviderTest {
    @Test
    void retriesFailuresAndRecordsTelemetry() {
        AtomicInteger calls = new AtomicInteger();
        AiEnrichmentProvider delegate = context -> {
            if (calls.incrementAndGet() < 3) throw new IllegalStateException("temporary");
            return List.of();
        };
        InMemoryProviderTelemetry telemetry = new InMemoryProviderTelemetry();
        var provider = new ResilientAiEnrichmentProvider(delegate, () -> true,
                new SimpleCircuitBreaker(3, Duration.ofSeconds(1)), telemetry, 3);

        provider.propose(context());

        assertEquals(3, calls.get());
        assertEquals(1, telemetry.successes());
    }

    @Test
    void recordsConfiguredEstimatedCost() {
        InMemoryProviderTelemetry telemetry = new InMemoryProviderTelemetry();
        var provider = new ResilientAiEnrichmentProvider(context -> List.of(), () -> true,
                new SimpleCircuitBreaker(2, Duration.ofSeconds(1)), telemetry, 1, 2.0, 3.0);
        provider.propose(context());
        assertTrue(telemetry.estimatedCost() > 0.0, "configured rates should produce an estimate");
    }

    @Test
    void failsFastWhenRateLimited() {
        var provider = new ResilientAiEnrichmentProvider(context -> List.of(), () -> false,
                new SimpleCircuitBreaker(2, Duration.ofSeconds(1)), new InMemoryProviderTelemetry(), 2);
        assertThrows(IllegalStateException.class, () -> provider.propose(context()));
    }

    private GroundedAssetContext context() {
        return new GroundedAssetContext(AssetMetadata.empty(AssetId.of("/content/dam/a.jpg")), List.of(), "v1");
    }
}
