package com.semanticdam.core.core.graph;

/** A graph read or write failed. Callers must not treat this as "no data". */
public class GraphStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public GraphStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
