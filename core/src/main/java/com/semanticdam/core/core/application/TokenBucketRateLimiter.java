package com.semanticdam.core.core.application;

import java.time.Duration;

/** Bounded, non-blocking limiter: rejected calls fail fast rather than queueing unbounded work. */
public final class TokenBucketRateLimiter implements RateLimiter {
    private final int capacity;
    private final double refillPerNanosecond;
    private double tokens;
    private long lastRefill;

    public TokenBucketRateLimiter(int permits, Duration window) {
        if (permits <= 0 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("invalid rate limit");
        }
        this.capacity = permits;
        this.tokens = permits;
        this.refillPerNanosecond = (double) permits / window.toNanos();
        this.lastRefill = System.nanoTime();
    }

    @Override
    public synchronized boolean tryAcquire() {
        long now = System.nanoTime();
        tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNanosecond);
        lastRefill = now;
        if (tokens < 1.0) return false;
        tokens -= 1.0;
        return true;
    }
}
