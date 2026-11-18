package com.semanticdam.core.core.rdf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recursive-descent parser for RDF 1.1 Turtle.
 *
 * <p>Covers the full triples grammar: directives ({@code @prefix}/{@code @base} and their SPARQL
 * forms), IRIs, prefixed names, the {@code a} keyword, predicate and object lists, blank node
 * labels and property lists, collections, all four string forms with escapes, language tags,
 * datatypes, and numeric and boolean shorthand. It deliberately does not implement full RFC 3986
 * relative IRI resolution: a relative IRI is appended to the base. The test suite compares its
 * output for the bundled ontology with Apache Jena.</p>
 */
public final class TurtleParser {
    private static final String XSD_INTEGER = RdfTerm.XSD + "integer";
    private static final String XSD_DECIMAL = RdfTerm.XSD + "decimal";
    private static final String XSD_DOUBLE = RdfTerm.XSD + "double";
    private static final String XSD_BOOLEAN = RdfTerm.XSD + "boolean";

    private final String input;
    private final Map<String, String> prefixes = new HashMap<>();
    private final List<RdfTriple> triples = new ArrayList<>();
    private String base = "";
    private int position;
    private int blankCounter;

    private final String blankPrefix;

    private TurtleParser(String input, String blankPrefix) {
        this.input = input;
        this.blankPrefix = blankPrefix;
    }

    public static RdfGraph parse(String turtle) {
        return parse(turtle, "b");
    }

    /**
     * @param blankPrefix prefix for generated blank node labels; use a distinct one per document
     *                    when the resulting graphs are merged
     */
    public static RdfGraph parse(String turtle, String blankPrefix) {
        if (turtle == null) {
            throw new IllegalArgumentException("turtle must be provided");
        }
        TurtleParser parser = new TurtleParser(turtle, blankPrefix);
        parser.document();
        return new RdfGraph(parser.triples);
    }

    private void document() {
        skipWhitespace();
        while (position < input.length()) {
            statement();
            skipWhitespace();
        }
    }

    private void statement() {
        if (peek() == '@') {
            position++;
            String keyword = bareWord();
            if ("prefix".equals(keyword)) {
                prefixDirective();
            } else if ("base".equals(keyword)) {
                baseDirective();
            } else {
                throw error("unknown directive @" + keyword);
            }
            expect('.');
            return;
        }
        if (lookingAtKeyword("PREFIX")) {
            position += "PREFIX".length();
            prefixDirective();
            return;
        }
        if (lookingAtKeyword("BASE")) {
            position += "BASE".length();
            baseDirective();
            return;
        }
        triplesStatement();
        expect('.');
    }

    private void prefixDirective() {
        skipWhitespace();
        int start = position;
        while (position < input.length() && input.charAt(position) != ':') {
            if (Character.isWhitespace(input.charAt(position))) {
                throw error("prefix name must end with ':'");
            }
            position++;
        }
        String name = input.substring(start, position);
        expect(':');
        skipWhitespace();
        prefixes.put(name, iriRef());
    }

    private void baseDirective() {
        skipWhitespace();
        base = iriRef();
    }

    private void triplesStatement() {
        skipWhitespace();
        RdfTerm subject;
        if (peek() == '[') {
            subject = blankNodePropertyList();
            skipWhitespace();
            if (peek() == '.') {
                return;
            }
        } else if (peek() == '(') {
            subject = collection();
        } else {
            subject = resource();
        }
        predicateObjectList(subject);
    }

    private void predicateObjectList(RdfTerm subject) {
        while (true) {
            skipWhitespace();
            RdfTerm predicate = verb();
            objectList(subject, predicate);
            skipWhitespace();
            boolean more = false;
            while (peek() == ';') {
                position++;
                skipWhitespace();
                more = true;
            }
            if (!more || peek() == '.' || peek() == ']') {
                return;
            }
        }
    }

    private void objectList(RdfTerm subject, RdfTerm predicate) {
        while (true) {
            skipWhitespace();
            triples.add(new RdfTriple(subject, predicate, object()));
            skipWhitespace();
            if (peek() != ',') {
                return;
            }
            position++;
        }
    }

    private RdfTerm verb() {
        if (peek() == 'a' && position + 1 < input.length() && isDelimiter(input.charAt(position + 1))) {
            position++;
            return RdfTerm.iri(RdfGraph.RDF_TYPE);
        }
        RdfTerm predicate = resource();
        if (!predicate.isIri()) {
            throw error("predicate must be an IRI");
        }
        return predicate;
    }

    private RdfTerm object() {
        char next = peek();
        if (next == '[') {
            return blankNodePropertyList();
        }
        if (next == '(') {
            return collection();
        }
        if (next == '"' || next == '\'') {
            return stringLiteral();
        }
        if (next == '+' || next == '-' || next == '.' || Character.isDigit(next)) {
            return numericLiteral();
        }
        if (lookingAtKeyword("true")) {
            position += 4;
            return RdfTerm.typedLiteral("true", XSD_BOOLEAN);
        }
        if (lookingAtKeyword("false")) {
            position += 5;
            return RdfTerm.typedLiteral("false", XSD_BOOLEAN);
        }
        return resource();
    }

    private RdfTerm resource() {
        char next = peek();
        if (next == '<') {
            return RdfTerm.iri(iriRef());
        }
        if (next == '_' && position + 1 < input.length() && input.charAt(position + 1) == ':') {
            position += 2;
            int start = position;
            while (position < input.length() && isNameChar(input.charAt(position))) {
                position++;
            }
            while (position > start && input.charAt(position - 1) == '.') {
                position--;
            }
            if (position == start) {
                throw error("blank node label expected");
            }
            return RdfTerm.blank(blankPrefix + "_" + input.substring(start, position));
        }
        return prefixedName();
    }

    private RdfTerm prefixedName() {
        int start = position;
        while (position < input.length() && input.charAt(position) != ':' && isNameChar(input.charAt(position))) {
            position++;
        }
        if (peek() != ':') {
            position = start;
            throw error("expected an IRI, prefixed name or blank node");
        }
        String prefix = input.substring(start, position);
        position++;
        StringBuilder local = new StringBuilder();
        while (position < input.length()) {
            char current = input.charAt(position);
            if (current == '\\' && position + 1 < input.length()) {
                local.append(input.charAt(position + 1));
                position += 2;
            } else if (current == '%' || current == ':' || isNameChar(current)) {
                local.append(current);
                position++;
            } else {
                break;
            }
        }
        while (local.length() > 0 && local.charAt(local.length() - 1) == '.') {
            local.setLength(local.length() - 1);
            position--;
        }
        String namespace = prefixes.get(prefix);
        if (namespace == null) {
            position = start;
            throw error("undeclared prefix '" + prefix + ":'");
        }
        return RdfTerm.iri(namespace + local);
    }

    private RdfTerm blankNodePropertyList() {
        expect('[');
        RdfTerm node = RdfTerm.blank(blankPrefix + (++blankCounter));
        skipWhitespace();
        if (peek() != ']') {
            predicateObjectList(node);
            skipWhitespace();
        }
        expect(']');
        return node;
    }

    private RdfTerm collection() {
        expect('(');
        List<RdfTerm> items = new ArrayList<>();
        skipWhitespace();
        while (peek() != ')') {
            items.add(object());
            skipWhitespace();
        }
        expect(')');
        RdfTerm head = RdfTerm.iri(RdfGraph.RDF_NIL);
        for (int i = items.size() - 1; i >= 0; i--) {
            RdfTerm cell = RdfTerm.blank(blankPrefix + (++blankCounter));
            triples.add(new RdfTriple(cell, RdfTerm.iri(RdfGraph.RDF_FIRST), items.get(i)));
            triples.add(new RdfTriple(cell, RdfTerm.iri(RdfGraph.RDF_REST), head));
            head = cell;
        }
        return head;
    }

    private RdfTerm stringLiteral() {
        char quote = input.charAt(position);
        boolean triple = input.startsWith(String.valueOf(quote).repeat(3), position);
        position += triple ? 3 : 1;
        StringBuilder value = new StringBuilder();
        while (true) {
            if (position >= input.length()) {
                throw error("unterminated string literal");
            }
            char current = input.charAt(position);
            if (current == '\\') {
                position++;
                value.append(escape());
                continue;
            }
            if (current == quote) {
                if (!triple) {
                    position++;
                    break;
                }
                if (input.startsWith(String.valueOf(quote).repeat(3), position)) {
                    position += 3;
                    break;
                }
            } else if (!triple && (current == '\n' || current == '\r')) {
                throw error("line break in single-quoted string literal");
            }
            value.append(current);
            position++;
        }
        if (peek() == '@') {
            position++;
            int start = position;
            while (position < input.length()
                    && (Character.isLetterOrDigit(input.charAt(position)) || input.charAt(position) == '-')) {
                position++;
            }
            if (position == start) {
                throw error("language tag expected");
            }
            return RdfTerm.langLiteral(value.toString(), input.substring(start, position));
        }
        if (input.startsWith("^^", position)) {
            position += 2;
            RdfTerm datatype = resource();
            if (!datatype.isIri()) {
                throw error("datatype must be an IRI");
            }
            return RdfTerm.typedLiteral(value.toString(), datatype.value());
        }
        return RdfTerm.literal(value.toString());
    }

    private String escape() {
        if (position >= input.length()) {
            throw error("dangling escape");
        }
        char code = input.charAt(position++);
        switch (code) {
            case 't': return "\t";
            case 'b': return "\b";
            case 'n': return "\n";
            case 'r': return "\r";
            case 'f': return "\f";
            case '"': return "\"";
            case '\'': return "'";
            case '\\': return "\\";
            case 'u': return unicode(4);
            case 'U': return unicode(8);
            default: throw error("invalid escape \\" + code);
        }
    }

    private String unicode(int digits) {
        if (position + digits > input.length()) {
            throw error("truncated unicode escape");
        }
        String hex = input.substring(position, position + digits);
        try {
            int codePoint = Integer.parseInt(hex, 16);
            position += digits;
            return new String(Character.toChars(codePoint));
        } catch (IllegalArgumentException ex) {
            throw error("invalid unicode escape " + hex);
        }
    }

    private RdfTerm numericLiteral() {
        int start = position;
        if (peek() == '+' || peek() == '-') {
            position++;
        }
        boolean digits = false;
        boolean decimal = false;
        boolean exponent = false;
        while (position < input.length() && Character.isDigit(input.charAt(position))) {
            position++;
            digits = true;
        }
        if (peek() == '.' && position + 1 < input.length() && Character.isDigit(input.charAt(position + 1))) {
            decimal = true;
            position++;
            while (position < input.length() && Character.isDigit(input.charAt(position))) {
                position++;
                digits = true;
            }
        }
        if (digits && (peek() == 'e' || peek() == 'E')) {
            int mark = position;
            position++;
            if (peek() == '+' || peek() == '-') {
                position++;
            }
            if (Character.isDigit(peek())) {
                exponent = true;
                while (position < input.length() && Character.isDigit(input.charAt(position))) {
                    position++;
                }
            } else {
                position = mark;
            }
        }
        if (!digits) {
            position = start;
            throw error("numeric literal expected");
        }
        String lexical = input.substring(start, position);
        return RdfTerm.typedLiteral(lexical, exponent ? XSD_DOUBLE : decimal ? XSD_DECIMAL : XSD_INTEGER);
    }

    private String iriRef() {
        expect('<');
        StringBuilder iri = new StringBuilder();
        while (true) {
            if (position >= input.length()) {
                throw error("unterminated IRI");
            }
            char current = input.charAt(position++);
            if (current == '>') {
                break;
            }
            if (current == '\\') {
                char code = position < input.length() ? input.charAt(position++) : ' ';
                if (code == 'u') {
                    iri.append(unicode(4));
                } else if (code == 'U') {
                    iri.append(unicode(8));
                } else {
                    throw error("invalid IRI escape");
                }
                continue;
            }
            if (current <= ' ' || current == '<' || current == '"' || current == '{' || current == '}'
                    || current == '|' || current == '^' || current == '`') {
                throw error("illegal character in IRI");
            }
            iri.append(current);
        }
        String value = iri.toString();
        return value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*") ? value : base + value;
    }

    private String bareWord() {
        int start = position;
        while (position < input.length() && Character.isLetter(input.charAt(position))) {
            position++;
        }
        return input.substring(start, position);
    }

    private boolean lookingAtKeyword(String keyword) {
        if (!input.regionMatches(true, position, keyword, 0, keyword.length())) {
            return false;
        }
        boolean caseSensitive = Character.isLowerCase(keyword.charAt(0));
        if (caseSensitive && !input.startsWith(keyword, position)) {
            return false;
        }
        int end = position + keyword.length();
        return end >= input.length() || isDelimiter(input.charAt(end));
    }

    private static boolean isDelimiter(char character) {
        return Character.isWhitespace(character) || character == '<' || character == '[' || character == '('
                || character == '"' || character == '\'' || character == '.' || character == ';'
                || character == ',' || character == ')' || character == ']' || character == '#';
    }

    private static boolean isNameChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-' || character == '.'
                || character == '·' || character > 0x7F;
    }

    private char peek() {
        return position < input.length() ? input.charAt(position) : '\0';
    }

    private void expect(char expected) {
        skipWhitespace();
        if (peek() != expected) {
            throw error("expected '" + expected + "'");
        }
        position++;
    }

    private void skipWhitespace() {
        while (position < input.length()) {
            char current = input.charAt(position);
            if (current == '#') {
                while (position < input.length() && input.charAt(position) != '\n') {
                    position++;
                }
            } else if (Character.isWhitespace(current)) {
                position++;
            } else {
                return;
            }
        }
    }

    private TurtleParseException error(String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < position && i < input.length(); i++) {
            if (input.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new TurtleParseException(message, line, column);
    }
}
