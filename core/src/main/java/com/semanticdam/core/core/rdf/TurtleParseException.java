package com.semanticdam.core.core.rdf;

/** Thrown when a Turtle document is not well formed. */
public class TurtleParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TurtleParseException(String message, int line, int column) {
        super(message + " (line " + line + ", column " + column + ")");
    }
}
