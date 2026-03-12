package com.example.aemassets.core.graph;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class HttpSparqlClient implements SparqlClient {
    private final String endpointUrl;
    private final String username;
    private final String password;

    public HttpSparqlClient(String endpointUrl, String username, String password) {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalArgumentException("endpointUrl must be provided");
        }
        this.endpointUrl = endpointUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public void update(String sparqlUpdate) {
        if (sparqlUpdate == null || sparqlUpdate.isBlank()) {
            throw new IllegalArgumentException("sparqlUpdate must be provided");
        }
        execute(sparqlUpdate, "application/sparql-update");
    }

    @Override
    public Optional<String> query(String sparqlQuery) {
        if (sparqlQuery == null || sparqlQuery.isBlank()) {
            throw new IllegalArgumentException("sparqlQuery must be provided");
        }
        return execute(sparqlQuery, "application/sparql-query");
    }

    private Optional<String> execute(String body, String contentType) {
        HttpPost post = new HttpPost(endpointUrl);
        post.setEntity(new StringEntity(body, ContentType.create(contentType, StandardCharsets.UTF_8)));
        if (username != null && !username.isBlank()) {
            String token = java.util.Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            post.setHeader("Authorization", "Basic " + token);
        }
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return Optional.empty();
            }
            return Optional.of(EntityUtils.toString(entity, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to execute SPARQL request", ex);
        }
    }
}
