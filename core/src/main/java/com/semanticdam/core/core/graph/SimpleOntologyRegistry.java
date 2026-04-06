package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.OntologyTerm;

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
        String[] blocks = turtle.split("\\n\\n");
        for (String block : blocks) {
            String trimmedBlock = block.trim();
            if (trimmedBlock.isEmpty()) continue;

            if (trimmedBlock.startsWith("@prefix")) {
                for (String line : trimmedBlock.split("\\n")) {
                    parsePrefix(line.trim(), prefixes);
                }
                continue;
            }

            // Simple block parser: subject predicate object ; predicate object .
            String[] lines = trimmedBlock.split("\\n");
            String subject = null;
            String label = null;
            String pattern = null;

            for (String line : lines) {
                String trimmedLine = stripComment(line).trim();
                if (trimmedLine.isEmpty()) continue;

                if (subject == null) {
                    subject = trimmedLine.split("\\s+")[0];
                }

                if (trimmedLine.contains("rdfs:label")) {
                    label = extractValue(trimmedLine, "rdfs:label");
                }
                if (trimmedLine.contains("aem:pattern")) {
                    pattern = extractValue(trimmedLine, "aem:pattern");
                }
            }

            if (subject != null) {
                String uri = resolveUri(subject, prefixes);
                if (uri != null) {
                    if (label == null) label = labelFromSubject(subject);
                    OntologyTerm term = new OntologyTerm(uri, label, pattern);
                    byUri.put(uri, term);
                    byLabel.put(normalizeLabel(label), term);
                }
            }
        }
    }

    private String extractValue(String line, String predicate) {
        int idx = line.indexOf(predicate);
        if (idx < 0) return null;
        String rest = line.substring(idx + predicate.length()).trim();
        if (rest.startsWith("\"")) {
            int end = rest.indexOf("\"", 1);
            if (end > 0) {
                return rest.substring(1, end);
            }
        }
        return null;
    }

    private void parsePrefix(String line, Map<String, String> prefixes) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3) return;
        String prefixToken = tokens[1];
        String uriToken = tokens[2];
        if (!prefixToken.endsWith(":")) return;
        String prefix = prefixToken.substring(0, prefixToken.length() - 1);
        String uri = uriToken.replace("<", "").replace(">", "");
        prefixes.put(prefix, uri);
    }

    private String resolveUri(String subject, Map<String, String> prefixes) {
        if (subject.startsWith("<") && subject.endsWith(">")) {
            return subject.substring(1, subject.length() - 1);
        }
        int colon = subject.indexOf(':');
        if (colon <= 0) return null;
        String prefix = subject.substring(0, colon);
        String local = subject.substring(colon + 1);
        String base = prefixes.get(prefix);
        if (base == null) return null;
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
        if (idx >= 0) return line.substring(0, idx);
        return line;
    }

    private String normalizeLabel(String label) {
        return label.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean isAllowedForBundle(String predicateUri, String bundleName) {
        // Implementation of Drupal-style "Entity Bundles"
        if ("ProductImage".equalsIgnoreCase(bundleName)) {
            return java.util.Set.of(
                "http://purl.org/dc/terms/title",
                "http://purl.org/dc/terms/description",
                "http://purl.org/dc/terms/format",
                "https://schema.org/brand",
                "https://schema.org/sku",
                "http://www.w3.org/2004/02/skos/core#prefLabel",
                "https://schema.org/contentLocation",
                "https://semanticdam.com/ontology/strategy#priority",
                "https://semanticdam.com/ontology/workfront#projectId",
                "https://semanticdam.com/ontology/workfront#campaignName"
            ).contains(predicateUri);
        }
        return true; // Default to open for other bundles for now
    }
}
