package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.GroundedAssetContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiCompatibleEnrichmentProviderTest {
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void postsBearerRequestAndParsesStrictProposalContent() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange,
                "{\"choices\":[{\"message\":{\"content\":\"[{\\\"predicateUri\\\":\\\"https://example.com/aem-assets-ontology#brand\\\",\\\"objectValue\\\":\\\"Example\\\",\\\"objectIsUri\\\":false,\\\"source\\\":\\\"gateway\\\",\\\"confidence\\\":0.95,\\\"rationale\\\":\\\"logo evidence\\\"}]\"}}]}"));
        String predicate = "https://example.com/aem-assets-ontology#brand";
        OpenAiCompatibleEnrichmentProvider provider = provider(predicate);

        var proposals = provider.propose(new GroundedAssetContext(
                AssetMetadata.empty(AssetId.of("/content/dam/a.jpg")), List.of(), "ontology-1"));

        assertEquals(1, proposals.size());
        assertEquals(predicate, proposals.iterator().next().statement().predicateUri());
        assertEquals("gateway", proposals.iterator().next().statement().source());
    }

    @Test
    void rejectsNonJsonContentAndPolicyViolations() {
        server.createContext("/bad", exchange -> respond(exchange,
                "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}"));
        OpenAiCompatibleEnrichmentProvider provider = new OpenAiCompatibleEnrichmentProvider(
                "http://localhost:" + server.getAddress().getPort() + "/bad", "secret", "model", "p1",
                Duration.ofSeconds(2), new AiEnrichmentPolicy(1, 0.8, List.of("allowed")));
        GroundedAssetContext context = new GroundedAssetContext(
                AssetMetadata.empty(AssetId.of("/content/dam/a.jpg")), List.of(), "ontology-1");

        assertThrows(IllegalStateException.class, () -> provider.propose(context));
    }

    private OpenAiCompatibleEnrichmentProvider provider(String predicate) {
        return new OpenAiCompatibleEnrichmentProvider(
                "http://localhost:" + server.getAddress().getPort() + "/v1/chat/completions",
                "secret", "model", "p1", Duration.ofSeconds(2),
                new AiEnrichmentPolicy(1, 0.8, List.of(predicate)));
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        assertEquals("Bearer secret", exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
