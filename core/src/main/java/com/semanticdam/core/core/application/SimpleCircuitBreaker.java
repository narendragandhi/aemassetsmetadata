package com.semanticdam.core.core.application;

import java.time.Duration;

public final class SimpleCircuitBreaker implements CircuitBreaker {
    private final int failureThreshold;
    private final long openNanos;
    private int failures;
    private long openedAt;

    public SimpleCircuitBreaker(int failureThreshold, Duration openDuration) {
        if (failureThreshold <= 0 || openDuration == null || openDuration.isZero() || openDuration.isNegative()) {
            throw new IllegalArgumentException("invalid circuit breaker configuration");
        }
        this.failureThreshold = failureThreshold;
        this.openNanos = openDuration.toNanos();
    }

    @Override
    public synchronized boolean allowRequest() {
        return openedAt == 0 || System.nanoTime() - openedAt >= openNanos;
    }

    @Override
    public synchronized void recordSuccess() {
        failures = 0;
        openedAt = 0;
    }

    @Override
    public synchronized void recordFailure() {
        if (++failures >= failureThreshold) openedAt = System.nanoTime();
    }
}
