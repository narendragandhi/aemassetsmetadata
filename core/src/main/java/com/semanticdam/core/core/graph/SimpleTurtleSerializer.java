package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;

import java.util.List;

/**
 * Writes triples as Turtle using full IRIs (no prefixes), one triple per line. The output is also
 * valid N-Triples and can be embedded in a SPARQL {@code INSERT DATA} block as is.
 */
public class SimpleTurtleSerializer implements GraphSerializer {

    @Override
    public String toTurtle(List<GraphTriple> triples) {
        StringBuilder sb = new StringBuilder();
        for (GraphTriple triple : triples) {
            sb.append(iri(triple.subject())).append(' ')
              .append(iri(triple.predicate())).append(' ')
              .append(triple.objectIsUri() ? iri(triple.object()) : literal(triple.object()))
              .append(" .\n");
        }
        return sb.toString();
    }

    public String serialize(AssetMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        String subject = iri(new GraphSubjectResolver().assetSubject(metadata.assetId()));
        for (MetadataStatement statement : metadata.statements()) {
            sb.append(subject).append(' ')
              .append(iri(statement.predicateUri())).append(' ')
              .append(statement.objectIsUri() ? iri(statement.objectValue()) : literal(statement.objectValue()))
              .append(" .\n");
        }
        return sb.toString();
    }

    /** Percent-encodes the characters that may not appear inside an IRI reference. */
    public static String iri(String value) {
        StringBuilder sb = new StringBuilder("<");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c == '<' || c == '>' || c == '"' || c == '{' || c == '}' || c == '|'
                    || c == '^' || c == '`' || c == '\\') {
                sb.append(String.format("%%%02X", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('>').toString();
    }

    public static String literal(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04X", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
