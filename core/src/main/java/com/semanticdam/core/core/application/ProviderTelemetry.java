package com.semanticdam.core.core.application;

/** Minimal metrics seam; connect this to Micrometer/OpenTelemetry in production. */
public interface ProviderTelemetry {
    void recordSuccess(long latencyMillis, int inputCharacters, int outputCharacters);
    void recordFailure(long latencyMillis);
    default void recordEstimatedCost(double cost) { }
}
