package com.example.aemassets.core.graph;

import java.util.List;
import java.util.stream.Collectors;

public class SimpleTurtleSerializer implements GraphSerializer {
    @Override
    public String toTurtle(List<GraphTriple> triples) {
        if (triples == null || triples.isEmpty()) {
            return "";
        }
        return triples.stream()
                .map(this::formatTriple)
                .collect(Collectors.joining("\n"));
    }

    private String formatTriple(GraphTriple triple) {
        String subject = wrapUri(triple.subject());
        String predicate = wrapUri(triple.predicate());
        String object = triple.objectIsUri()
                ? wrapUri(triple.object())
                : \"\\\"\" + escape(triple.object()) + \"\\\"\";
        return subject + " " + predicate + " " + object + " .";
    }

    private String wrapUri(String uri) {
        if (uri.startsWith("<") && uri.endsWith(">")) {
            return uri;
        }
        return "<" + uri + ">";
    }

    private String escape(String value) {
        return value.replace("\\\\", "\\\\\\\\").replace("\"", "\\\\\"");
    }
}
