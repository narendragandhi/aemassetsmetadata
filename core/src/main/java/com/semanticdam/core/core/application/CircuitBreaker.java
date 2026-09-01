package com.semanticdam.core.core.application;

public interface CircuitBreaker {
    boolean allowRequest();
    void recordSuccess();
    void recordFailure();
}
