package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.EmbeddingVector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Qdrant REST adapter. The collection must be created with matching size and Cosine distance. */
public final class QdrantVectorSearchIndex implements VectorSearchIndex {
    private final HttpClient client;
    private final String baseUrl;
    private final String apiKey;
    private final String collection;
    private final String modelId;
    private final String modelVersion;
    private final Duration timeout;

    public QdrantVectorSearchIndex(String baseUrl, String apiKey, String collection,
            String modelId, String modelVersion, Duration timeout) {
        if (baseUrl == null || baseUrl.isBlank() || collection == null || collection.isBlank()
                || modelId == null || modelId.isBlank() || modelVersion == null || modelVersion.isBlank()
                || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("complete Qdrant configuration must be provided");
        }
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.collection = collection;
        this.modelId = modelId;
        this.modelVersion = modelVersion;
        this.timeout = timeout;
    }

    @Override
    public void upsert(AssetId assetId, EmbeddingVector embedding) {
        validate(assetId, embedding);
        String pointId = UUID.nameUUIDFromBytes(assetId.path().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        String body = "{\"points\":[{\"id\":\"" + pointId + "\",\"vector\":" + vectorJson(embedding)
                + ",\"payload\":{\"assetId\":\"" + JsonEscaper.escape(assetId.path())
                + "\",\"modelId\":\"" + JsonEscaper.escape(modelId) + "\",\"modelVersion\":\""
                + JsonEscaper.escape(modelVersion) + "\"}}]}";
        request("PUT", "/collections/" + collection + "/points?wait=true", body);
    }

    @Override
    public void remove(AssetId assetId) {
        if (assetId == null) throw new IllegalArgumentException("assetId must be provided");
        String pointId = UUID.nameUUIDFromBytes(assetId.path().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        request("POST", "/collections/" + collection + "/points/delete?wait=true",
                "{\"points\":[\"" + pointId + "\"]}");
    }

    @Override
    public List<VectorMatch> search(EmbeddingVector query, int limit) {
        if (query == null || limit <= 0) throw new IllegalArgumentException("query and positive limit are required");
        if (!modelId.equals(query.modelId()) || !modelVersion.equals(query.modelVersion())) return List.of();
        String response = request("POST", "/collections/" + collection + "/points/search",
                "{\"vector\":" + vectorJson(query) + ",\"limit\":" + limit + ",\"with_payload\":true}");
        List<VectorMatch> matches = new ArrayList<>();
        for (String object : objectsAfter(response, "result")) {
            String assetId = stringValue(object, "assetId");
            String score = rawValue(object, "score");
            if (assetId != null && score != null) matches.add(new VectorMatch(AssetId.of(assetId), Double.parseDouble(score)));
        }
        return List.copyOf(matches);
    }

    private void validate(AssetId assetId, EmbeddingVector embedding) {
        if (assetId == null || embedding == null) throw new IllegalArgumentException("assetId and embedding are required");
        if (embedding.dimension() == 0 || !modelId.equals(embedding.modelId()) || !modelVersion.equals(embedding.modelVersion())) {
            throw new IllegalArgumentException("embedding model or version does not match index");
        }
    }

    private String request(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout)
                .header("Content-Type", "application/json");
        if (!apiKey.isBlank()) builder.header("api-key", apiKey);
        if ("PUT".equals(method)) builder.PUT(HttpRequest.BodyPublishers.ofString(body));
        else builder.POST(HttpRequest.BodyPublishers.ofString(body));
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Qdrant returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException ex) {
            throw new IllegalStateException("Qdrant request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant request interrupted", ex);
        }
    }

    private String vectorJson(EmbeddingVector vector) {
        return java.util.Arrays.stream(vector.values()).mapToObj(Double::toString).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static List<String> objectsAfter(String json, String key) {
        int start = json.indexOf("\"" + key + "\":");
        if (start < 0) return List.of();
        return objects(json.substring(start));
    }

    private static List<String> objects(String json) {
        List<String> result = new ArrayList<>(); int depth = 0; int start = -1; boolean quoted = false; boolean escaped = false;
        for (int i = 0; i < json.length(); i++) { char c = json.charAt(i); if (escaped) { escaped = false; continue; } if (c == '\\' && quoted) { escaped = true; continue; } if (c == '"') { quoted = !quoted; continue; } if (quoted) continue; if (c == '{') { if (depth++ == 0) start = i; } if (c == '}' && --depth == 0) { result.add(json.substring(start, i + 1)); start = -1; } }
        return result;
    }

    private static String stringValue(String json, String key) { String raw = rawValue(json, key); return raw != null && raw.startsWith("\"") ? raw.substring(1, raw.length() - 1) : null; }
    private static String rawValue(String json, String key) { int p = json.indexOf("\"" + key + "\":"); if (p < 0) return null; int s = p + key.length() + 3; while (s < json.length() && Character.isWhitespace(json.charAt(s))) s++; if (json.charAt(s) == '"') { int e = s + 1; while (e < json.length() && json.charAt(e) != '"') e++; return json.substring(s, Math.min(e + 1, json.length())); } int e = s; while (e < json.length() && ",}".indexOf(json.charAt(e)) < 0) e++; return json.substring(s, e).trim(); }
}
