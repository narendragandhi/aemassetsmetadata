package com.example.aemassets.core.graph;

import com.example.aemassets.core.domain.OntologyTerm;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class SimpleOntologyRegistry implements OntologyRegistry {
    private final Map<String, OntologyTerm> byUri = new HashMap<>();
    private final Map<String, OntologyTerm> byLabel = new HashMap<>();

    public SimpleOntologyRegistry(OntologySource source) {
        if (source == null) {
            throw new IllegalArgumentException("source must be provided");
        }
        parse(source.readTurtle());
    }

    @Override
    public Optional<OntologyTerm> findByUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byUri.get(uri));
    }

    @Override
    public Optional<OntologyTerm> findByLabel(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byLabel.get(normalizeLabel(label)));
    }

    private void parse(String turtle) {
        Map<String, String> prefixes = new HashMap<>();
        String[] lines = turtle.split("\\R");
        for (String line : lines) {
            String trimmed = stripComment(line).trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("@prefix")) {
                parsePrefix(trimmed, prefixes);
                continue;
            }
            if (trimmed.contains(" a ")) {
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length < 3) {
                    continue;
                }
                String subject = tokens[0];
                String uri = resolveUri(subject, prefixes);
                if (uri == null) {
                    continue;
                }
                String label = labelFromSubject(subject);
                OntologyTerm term = new OntologyTerm(uri, label);
                byUri.put(uri, term);
                byLabel.put(normalizeLabel(label), term);
            }
        }
    }

    private void parsePrefix(String line, Map<String, String> prefixes) {
        // Expected format: @prefix aem: <https://example.com/> .
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3) {
            return;
        }
        String prefixToken = tokens[1];
        String uriToken = tokens[2];
        if (!prefixToken.endsWith(":")) {
            return;
        }
        String prefix = prefixToken.substring(0, prefixToken.length() - 1);
        String uri = uriToken.replace("<", "").replace(">", "");
        prefixes.put(prefix, uri);
    }

    private String resolveUri(String subject, Map<String, String> prefixes) {
        if (subject.startsWith("<") && subject.endsWith(">")) {
            return subject.substring(1, subject.length() - 1);
        }
        int colon = subject.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String prefix = subject.substring(0, colon);
        String local = subject.substring(colon + 1);
        String base = prefixes.get(prefix);
        if (base == null) {
            return null;
        }
        return base + local;
    }

    private String labelFromSubject(String subject) {
        int colon = subject.indexOf(':');
        if (colon >= 0 && colon + 1 < subject.length()) {
            return subject.substring(colon + 1);
        }
        return subject;
    }

    private String stripComment(String line) {
        int idx = line.indexOf('#');
        if (idx >= 0) {
            return line.substring(0, idx);
        }
        return line;
    }

    private String normalizeLabel(String label) {
        return label.trim().toLowerCase(Locale.ROOT);
    }
}
