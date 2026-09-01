package com.semanticdam.core.core.application;

public interface RateLimiter {
    boolean tryAcquire();
}
