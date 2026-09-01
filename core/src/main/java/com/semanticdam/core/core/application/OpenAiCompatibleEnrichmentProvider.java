package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.GroundedAssetContext;
import com.semanticdam.core.core.domain.MetadataStatement;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Strict adapter for OpenAI-compatible chat-completions gateways.
 * The gateway response must contain JSON proposal objects in the first
 * choice's message content. No response is written to AEM by this class.
 */
public final class OpenAiCompatibleEnrichmentProvider implements AiEnrichmentProvider {
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String modelId;
    private final String promptVersion;
    private final Duration timeout;
    private final AiEnrichmentPolicy policy;

    public OpenAiCompatibleEnrichmentProvider(String endpoint, String apiKey, String modelId,
            String promptVersion, Duration timeout, AiEnrichmentPolicy policy) {
        this(HttpClient.newBuilder().connectTimeout(requireTimeout(timeout)).build(), endpoint, apiKey,
                modelId, promptVersion, timeout, policy);
    }

    OpenAiCompatibleEnrichmentProvider(HttpClient httpClient, String endpoint, String apiKey, String modelId,
            String promptVersion, Duration timeout, AiEnrichmentPolicy policy) {
        if (httpClient == null || endpoint == null || endpoint.isBlank() || apiKey == null || apiKey.isBlank()
                || modelId == null || modelId.isBlank() || promptVersion == null || promptVersion.isBlank()
                || timeout == null || timeout.isNegative() || timeout.isZero() || policy == null) {
            throw new IllegalArgumentException("complete gateway configuration must be provided");
        }
        this.httpClient = httpClient;
        this.endpoint = URI.create(endpoint);
        if (!"http".equalsIgnoreCase(this.endpoint.getScheme())
                && !"https".equalsIgnoreCase(this.endpoint.getScheme())) {
            throw new IllegalArgumentException("endpoint must use HTTP or HTTPS");
        }
        this.apiKey = apiKey;
        this.modelId = modelId;
        this.promptVersion = promptVersion;
        this.timeout = timeout;
        this.policy = policy;
    }

    @Override
    public Collection<AiEnrichmentProposal> propose(GroundedAssetContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must be provided");
        }
        String requestBody = requestBody(context);
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        try {
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI gateway returned HTTP " + response.statusCode());
            }
            Collection<AiEnrichmentProposal> proposals = parseResponse(response.body(), context);
            policy.validate(proposals);
            return List.copyOf(proposals);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI gateway request interrupted", ex);
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("AI gateway request failed", ex);
        }
    }

    private String requestBody(GroundedAssetContext context) {
        StringBuilder groundedFacts = new StringBuilder();
        appendFacts(groundedFacts, context.asset());
        context.relatedAssets().forEach(asset -> appendFacts(groundedFacts, asset));
        String system = "Return only a JSON array of objects with fields "
                + "predicateUri, objectValue, objectIsUri, source, confidence, rationale. "
                + "Use canonical predicates from ontology version " + context.ontologyVersion() + ".";
        return "{\"model\":\"" + JsonEscaper.escape(modelId) + "\",\"temperature\":0,"
                + "\"messages\":[{\"role\":\"system\",\"content\":\"" + JsonEscaper.escape(system)
                + "\"},{\"role\":\"user\",\"content\":\"Asset: "
                + JsonEscaper.escape(context.asset().assetId().path() + "\\n" + groundedFacts) + "\"}]}";
    }

    private void appendFacts(StringBuilder target, com.semanticdam.core.core.domain.AssetMetadata metadata) {
        for (MetadataStatement statement : metadata.statements()) {
            target.append(statement.predicateUri()).append('=').append(statement.objectValue()).append('\n');
        }
    }

    private Collection<AiEnrichmentProposal> parseResponse(String response, GroundedAssetContext context) {
        String content = extractString(response, "content");
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("AI gateway response has no message content");
        }
        if (!content.trim().startsWith("[")) {
            throw new IllegalStateException("AI gateway content is not a JSON array");
        }
        List<AiEnrichmentProposal> proposals = new ArrayList<>();
        for (String object : arrayObjects(content)) {
            String predicate = requiredString(object, "predicateUri");
            String value = requiredString(object, "objectValue");
            String source = requiredString(object, "source");
            String rationale = requiredString(object, "rationale");
            boolean objectIsUri = requiredBoolean(object, "objectIsUri");
            double confidence = requiredDouble(object, "confidence");
            MetadataStatement statement = objectIsUri
                    ? MetadataStatement.uri(predicate, value, source, true, confidence)
                    : MetadataStatement.literal(predicate, value, source, true, confidence);
            proposals.add(AiEnrichmentProposal.proposed(context.asset().assetId(), statement, modelId,
                    promptVersion, rationale));
        }
        return proposals;
    }

    private static List<String> arrayObjects(String content) {
        List<String> objects = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int start = -1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') { if (depth++ == 0) start = i; }
            if (c == '}' && --depth == 0 && start >= 0) { objects.add(content.substring(start, i + 1)); start = -1; }
        }
        if (depth != 0 || objects.isEmpty() && !content.trim().equals("[]")) {
            throw new IllegalStateException("AI gateway content is malformed");
        }
        return objects;
    }

    private static String requiredString(String json, String key) {
        String value = extractString(json, key);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing response field: " + key);
        return value;
    }

    private static boolean requiredBoolean(String json, String key) {
        String value = rawValue(json, key);
        if (!"true".equals(value) && !"false".equals(value)) throw new IllegalStateException("invalid response field: " + key);
        return Boolean.parseBoolean(value);
    }

    private static double requiredDouble(String json, String key) {
        try { return Double.parseDouble(rawValue(json, key)); }
        catch (RuntimeException ex) { throw new IllegalStateException("invalid response field: " + key, ex); }
    }

    private static String extractString(String json, String key) {
        String value = rawValue(json, key);
        if (value == null || !value.startsWith("\"")) return null;
        return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
    }

    private static String rawValue(String json, String key) {
        String marker = "\"" + key + "\":";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) return null;
        int start = keyIndex + marker.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            boolean escaped = false;
            for (int i = start + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (c == '"') return json.substring(start, i + 1);
            }
            return null;
        }
        int end = start;
        while (end < json.length() && ",}".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(start, end).trim();
    }

    private static Duration requireTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        return timeout;
    }
}
