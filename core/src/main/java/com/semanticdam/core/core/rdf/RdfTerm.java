package com.semanticdam.core.core.rdf;

import java.util.Objects;

/** An RDF term: IRI, blank node, or literal. */
public final class RdfTerm {
    public enum Kind { IRI, BLANK, LITERAL }

    public static final String XSD = "http://www.w3.org/2001/XMLSchema#";
    public static final String XSD_STRING = XSD + "string";
    public static final String RDF_LANG_STRING = "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString";

    private final Kind kind;
    private final String value;
    private final String datatype;
    private final String language;

    private RdfTerm(Kind kind, String value, String datatype, String language) {
        this.kind = kind;
        this.value = Objects.requireNonNull(value, "value");
        this.datatype = datatype;
        this.language = language;
    }

    public static RdfTerm iri(String iri) {
        return new RdfTerm(Kind.IRI, iri, null, null);
    }

    public static RdfTerm blank(String label) {
        return new RdfTerm(Kind.BLANK, label, null, null);
    }

    public static RdfTerm literal(String lexical) {
        return new RdfTerm(Kind.LITERAL, lexical, XSD_STRING, null);
    }

    public static RdfTerm typedLiteral(String lexical, String datatype) {
        return new RdfTerm(Kind.LITERAL, lexical, datatype, null);
    }

    public static RdfTerm langLiteral(String lexical, String language) {
        return new RdfTerm(Kind.LITERAL, lexical, RDF_LANG_STRING, language.toLowerCase(java.util.Locale.ROOT));
    }

    public Kind kind() { return kind; }
    public String value() { return value; }
    /** Datatype IRI for literals, otherwise {@code null}. */
    public String datatype() { return datatype; }
    /** Lower-cased language tag for language-tagged literals, otherwise {@code null}. */
    public String language() { return language; }
    public boolean isIri() { return kind == Kind.IRI; }
    public boolean isBlank() { return kind == Kind.BLANK; }
    public boolean isLiteral() { return kind == Kind.LITERAL; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RdfTerm that)) return false;
        return kind == that.kind && value.equals(that.value)
                && Objects.equals(datatype, that.datatype) && Objects.equals(language, that.language);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value, datatype, language);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case IRI -> "<" + value + ">";
            case BLANK -> "_:" + value;
            case LITERAL -> "\"" + value + "\"" + (language != null ? "@" + language : "^^<" + datatype + ">");
        };
    }
}
