package com.semanticdam.core.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.EmbeddingVector;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Qdrant REST adapter. The collection is created on first use with the embedding's dimension and
 * cosine distance. Each asset is one point whose id is derived from the asset path, so a re-index
 * replaces it.
 */
public final class QdrantVectorSearchIndex implements VectorSearchIndex {
    private final HttpClient client;
    private final String baseUrl;
    private final String apiKey;
    private final String collection;
    private final String modelId;
    private final String modelVersion;
    private final Duration timeout;
    private volatile boolean collectionReady;

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
        this.collection = URLEncoder.encode(collection, StandardCharsets.UTF_8);
        this.modelId = modelId;
        this.modelVersion = modelVersion;
        this.timeout = timeout;
    }

    @Override
    public void upsert(AssetId assetId, EmbeddingVector embedding) {
        validate(assetId, embedding);
        ensureCollection(embedding.dimension());
        JsonObject payload = new JsonObject();
        payload.addProperty("assetId", assetId.path());
        payload.addProperty("modelId", modelId);
        payload.addProperty("modelVersion", modelVersion);
        JsonObject point = new JsonObject();
        point.addProperty("id", pointId(assetId));
        point.add("vector", vector(embedding));
        point.add("payload", payload);
        JsonArray points = new JsonArray();
        points.add(point);
        JsonObject body = new JsonObject();
        body.add("points", points);
        request("PUT", "/collections/" + collection + "/points?wait=true", body.toString(), false);
    }

    @Override
    public void remove(AssetId assetId) {
        if (assetId == null) throw new IllegalArgumentException("assetId must be provided");
        JsonArray points = new JsonArray();
        points.add(pointId(assetId));
        JsonObject body = new JsonObject();
        body.add("points", points);
        // A missing collection means there is nothing to remove.
        request("POST", "/collections/" + collection + "/points/delete?wait=true", body.toString(), true);
    }

    @Override
    public List<VectorMatch> search(EmbeddingVector query, int limit) {
        if (query == null || limit <= 0) throw new IllegalArgumentException("query and positive limit are required");
        if (!modelId.equals(query.modelId()) || !modelVersion.equals(query.modelVersion())) {
            throw new IllegalStateException("query embedding model " + query.modelId() + "/" + query.modelVersion()
                    + " does not match the index model " + modelId + "/" + modelVersion);
        }
        JsonObject body = new JsonObject();
        body.add("vector", vector(query));
        body.addProperty("limit", limit);
        body.addProperty("with_payload", true);
        String response = request("POST", "/collections/" + collection + "/points/search", body.toString(), true);
        List<VectorMatch> matches = new ArrayList<>();
        if (response == null) {
            return matches;
        }
        for (JsonElement hit : JsonParser.parseString(response).getAsJsonObject().getAsJsonArray("result")) {
            JsonObject object = hit.getAsJsonObject();
            JsonObject hitPayload = object.getAsJsonObject("payload");
            if (hitPayload != null && hitPayload.has("assetId")
                    && modelVersion.equals(hitPayload.has("modelVersion") ? hitPayload.get("modelVersion").getAsString() : null)) {
                matches.add(new VectorMatch(AssetId.of(hitPayload.get("assetId").getAsString()),
                        object.get("score").getAsDouble()));
            }
        }
        return List.copyOf(matches);
    }

    private void ensureCollection(int dimension) {
        if (collectionReady) {
            return;
        }
        if (request("GET", "/collections/" + collection, null, true) == null) {
            JsonObject vectors = new JsonObject();
            vectors.addProperty("size", dimension);
            vectors.addProperty("distance", "Cosine");
            JsonObject body = new JsonObject();
            body.add("vectors", vectors);
            request("PUT", "/collections/" + collection, body.toString(), false);
        }
        collectionReady = true;
    }

    private void validate(AssetId assetId, EmbeddingVector embedding) {
        if (assetId == null || embedding == null) throw new IllegalArgumentException("assetId and embedding are required");
        if (!modelId.equals(embedding.modelId()) || !modelVersion.equals(embedding.modelVersion())) {
            throw new IllegalStateException("embedding model " + embedding.modelId() + "/" + embedding.modelVersion()
                    + " does not match the index model " + modelId + "/" + modelVersion);
        }
    }

    private static String pointId(AssetId assetId) {
        return UUID.nameUUIDFromBytes(assetId.path().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static JsonArray vector(EmbeddingVector embedding) {
        JsonArray values = new JsonArray();
        for (double value : embedding.values()) {
            values.add(value);
        }
        return values;
    }

    /** @return the body, or null when {@code notFoundIsEmpty} and Qdrant answered 404 */
    private String request(String method, String path, String body, boolean notFoundIsEmpty) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout)
                .header("Content-Type", "application/json");
        if (!apiKey.isBlank()) builder.header("api-key", apiKey);
        HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        builder.method(method, publisher);
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 && notFoundIsEmpty) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Qdrant returned HTTP " + response.statusCode() + " for " + method + " " + path);
            }
            return response.body();
        } catch (IOException ex) {
            throw new IllegalStateException("Qdrant request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant request interrupted", ex);
        }
    }
}
