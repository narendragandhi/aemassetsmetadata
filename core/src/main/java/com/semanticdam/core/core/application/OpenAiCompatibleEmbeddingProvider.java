package com.semanticdam.core.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.semanticdam.core.core.domain.EmbeddingVector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for an OpenAI-compatible {@code /embeddings} endpoint (OpenAI, Azure OpenAI through a
 * gateway, Ollama, vLLM, LiteLLM). The vector is tagged with the configured model id and version so
 * that it is never compared with vectors from another model.
 */
public final class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {
    private final HttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final String modelId;
    private final String modelVersion;
    private final Duration timeout;

    public OpenAiCompatibleEmbeddingProvider(String endpoint, String apiKey, String modelId, String modelVersion,
            Duration timeout) {
        this(HttpClient.newBuilder().connectTimeout(timeout).build(), endpoint, apiKey, modelId, modelVersion, timeout);
    }

    OpenAiCompatibleEmbeddingProvider(HttpClient client, String endpoint, String apiKey, String modelId,
            String modelVersion, Duration timeout) {
        if (client == null || endpoint == null || endpoint.isBlank() || modelId == null || modelId.isBlank()
                || modelVersion == null || modelVersion.isBlank() || timeout == null || timeout.isZero()
                || timeout.isNegative()) {
            throw new IllegalArgumentException("complete embedding configuration must be provided");
        }
        this.client = client;
        this.endpoint = URI.create(endpoint);
        if (!"http".equalsIgnoreCase(this.endpoint.getScheme()) && !"https".equalsIgnoreCase(this.endpoint.getScheme())) {
            throw new IllegalArgumentException("endpoint must use HTTP or HTTPS");
        }
        this.apiKey = apiKey == null ? "" : apiKey;
        this.modelId = modelId;
        this.modelVersion = modelVersion;
        this.timeout = timeout;
    }

    @Override
    public EmbeddingVector embed(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must be provided");
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", modelId);
        body.addProperty("input", input);
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (!apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        String response;
        try {
            HttpResponse<String> reply = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (reply.statusCode() < 200 || reply.statusCode() >= 300) {
                throw new IllegalStateException("Embedding endpoint returned HTTP " + reply.statusCode());
            }
            response = reply.body();
        } catch (IOException e) {
            throw new IllegalStateException("Embedding request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding request interrupted", e);
        }
        try {
            JsonArray values = JsonParser.parseString(response).getAsJsonObject().getAsJsonArray("data")
                    .get(0).getAsJsonObject().getAsJsonArray("embedding");
            double[] vector = new double[values.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = values.get(i).getAsDouble();
            }
            return EmbeddingVector.of(modelId, modelVersion, vector);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Embedding response is malformed", e);
        }
    }

    public String modelId() {
        return modelId;
    }

    public String modelVersion() {
        return modelVersion;
    }
}
