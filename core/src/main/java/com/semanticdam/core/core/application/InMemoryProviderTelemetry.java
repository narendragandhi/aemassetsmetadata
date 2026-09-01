package com.semanticdam.core.core.application;

import java.util.concurrent.atomic.AtomicLong;

/** Reference telemetry sink for tests and local demos. */
public final class InMemoryProviderTelemetry implements ProviderTelemetry {
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong inputCharacters = new AtomicLong();
    private final AtomicLong outputCharacters = new AtomicLong();
    private final java.util.concurrent.atomic.DoubleAdder estimatedCost = new java.util.concurrent.atomic.DoubleAdder();

    @Override
    public void recordSuccess(long latencyMillis, int inputCharacters, int outputCharacters) {
        successes.incrementAndGet();
        this.inputCharacters.addAndGet(inputCharacters);
        this.outputCharacters.addAndGet(outputCharacters);
    }

    @Override
    public void recordFailure(long latencyMillis) {
        failures.incrementAndGet();
    }

    @Override
    public void recordEstimatedCost(double cost) { estimatedCost.add(cost); }

    public long successes() { return successes.get(); }
    public long failures() { return failures.get(); }
    public long inputCharacters() { return inputCharacters.get(); }
    public long outputCharacters() { return outputCharacters.get(); }
    public double estimatedCost() { return estimatedCost.sum(); }
}
