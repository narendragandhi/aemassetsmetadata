package com.semanticdam.core.core.graph;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/** SPARQL 1.1 Protocol client: queries and updates sent by POST, results requested as JSON. */
public class HttpSparqlClient implements SparqlClient {
    private final HttpClient client;
    private final URI queryEndpoint;
    private final URI updateEndpoint;
    private final String authorization;
    private final Duration timeout;

    public HttpSparqlClient(String endpointUrl, String username, String password) {
        this(endpointUrl, endpointUrl, username, password, Duration.ofSeconds(30));
    }

    /**
     * @param updateUrl the update endpoint; many stores (Fuseki, GraphDB) use a separate one
     */
    public HttpSparqlClient(String queryUrl, String updateUrl, String username, String password, Duration timeout) {
        if (queryUrl == null || queryUrl.isBlank()) {
            throw new IllegalArgumentException("endpointUrl must be provided");
        }
        this.queryEndpoint = URI.create(queryUrl);
        this.updateEndpoint = URI.create(updateUrl == null || updateUrl.isBlank() ? queryUrl : updateUrl);
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.authorization = username == null || username.isBlank() ? null
                : "Basic " + Base64.getEncoder().encodeToString(
                        (username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void update(String sparqlUpdate) {
        if (sparqlUpdate == null || sparqlUpdate.isBlank()) {
            throw new IllegalArgumentException("sparqlUpdate must be provided");
        }
        send(updateEndpoint, sparqlUpdate, "application/sparql-update");
    }

    @Override
    public Optional<String> query(String sparqlQuery) {
        if (sparqlQuery == null || sparqlQuery.isBlank()) {
            throw new IllegalArgumentException("sparqlQuery must be provided");
        }
        return Optional.of(send(queryEndpoint, sparqlQuery, "application/sparql-query"));
    }

    private String send(URI endpoint, String body, String contentType) {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", contentType + "; charset=UTF-8")
                .header("Accept", "application/sparql-results+json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("SPARQL endpoint returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException ex) {
            throw new IllegalStateException("SPARQL request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SPARQL request interrupted", ex);
        }
    }
}
