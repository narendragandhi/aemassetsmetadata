package com.semanticdam.core.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.semanticdam.core.core.TestOntology;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.EmbeddingVector;
import com.semanticdam.core.core.graph.InMemoryKnowledgeGraphStore;
import com.semanticdam.core.core.ontology.OntologyModel;
import com.sun.net.httpserver.HttpServer;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobConsumer.JobResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.event.EventAdmin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticSearchTest {
    private static final String SKI = "/content/dam/site/ski.jpg";
    private static final String BEACH = "/content/dam/site/beach.jpg";
    private static final String SECRET = "/content/dam/embargo/ski-secret.jpg";
    private static final String EMPTY = "/content/dam/site/empty.jpg";

    /** Deterministic bag-of-words embedding: similar words give similar vectors, no network. */
    static final class WordEmbedding implements EmbeddingProvider {
        final List<String> inputs = new ArrayList<>();

        @Override
        public EmbeddingVector embed(String input) {
            inputs.add(input);
            double[] vector = new double[64];
            for (String word : input.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
                if (word.length() > 2) {
                    vector[Math.floorMod(word.hashCode(), 64)] += 1;
                }
            }
            vector[63] += 0.01;
            return EmbeddingVector.of("words", "1", vector);
        }
    }

    private final OntologyModel ontology = TestOntology.load();
    private final InMemoryKnowledgeGraphStore graph = new InMemoryKnowledgeGraphStore();
    private final WordEmbedding embeddings = new WordEmbedding();
    private final InMemoryVectorSearchIndex index = new InMemoryVectorSearchIndex("words", "1");
    private SemanticSearchService search;
    private AssetContextService context;

    @BeforeEach
    void setUp() {
        MapBackedAemMetadataSource dam = new MapBackedAemMetadataSource();
        MetadataValidator validator = new MetadataValidator(ontology);
        TagResolver tags = ids -> Map.of("wknd:activity/skiing", new TagResolver.TagInfo("wknd:activity/skiing", "Skiing", null));
        DefaultAssetMetadataPipeline pipeline = new DefaultAssetMetadataPipeline(dam,
                new AssetMetadataExtractionService(new AemAssetMetadataMapper(ontology), tags),
                new AssetMetadataIndexingService(graph),
                new SemanticEventService(ontology, validator, mock(EventAdmin.class)),
                new InferenceService(ontology), validator, ontology, graph);
        graph.writeEntity(new com.semanticdam.core.core.domain.Entity("https://semanticdam.com/id/brand/summit",
                com.semanticdam.core.core.ontology.Vocabulary.BRAND_CLASS, "Summit"));
        dam.put(AssetId.of(SKI), Map.of("dc:title", "Powder day", "cq:tags", new String[] {"wknd:activity/skiing"},
                "dc:description", "Fresh snow on the mountain slopes", "aem:brand", "Summit"));
        dam.put(AssetId.of(BEACH), Map.of("dc:title", "Surf session", "dc:description", "Waves on a sunny beach",
                "aem:brand", "Summit"));
        dam.put(AssetId.of(SECRET), Map.of("dc:title", "Unreleased skiing campaign",
                "cq:tags", new String[] {"wknd:activity/skiing"}, "dc:description", "Snow mountain slopes"));
        dam.put(AssetId.of(EMPTY), Map.of("xmp:CreatorTool", "Photoshop"));
        search = new SemanticSearchService(embeddings, index, graph, ontology);
        for (String path : List.of(SKI, BEACH, SECRET, EMPTY)) {
            pipeline.ingest(AssetId.of(path));
            search.index(AssetId.of(path));
        }
        ConflictResolutionServiceComponent conflicts = new ConflictResolutionServiceComponent();
        conflicts.setOntology(ontology);
        context = new AssetContextService(graph, ontology, conflicts, validator, new GraphTraversalService(graph));
        context.setSemanticSearch(search);
    }

    @Test
    void embedsGovernedFactsWithLabelsNotIris() {
        String text = search.text(graph.findByAssetId(AssetId.of(SKI)).orElseThrow());

        assertTrue(text.contains("Title: Powder day."), text);
        assertTrue(text.contains("Brand: Summit."), text);
        assertTrue(text.contains("Subject: Skiing."), text);
        assertFalse(text.contains("https://"), text);
    }

    @Test
    void ranksByMeaningAndSkipsAssetsWithoutFacts() {
        JsonArray results = context.semanticSearch("snow skiing mountain", null, null, 10, path -> true);

        assertEquals(SKI.equals(path(results, 0)) || SECRET.equals(path(results, 0)), true);
        assertEquals(BEACH, path(results, results.size() - 1));
        assertEquals(3, results.size());
        assertTrue(results.get(0).getAsJsonObject().get("score").getAsDouble()
                > results.get(2).getAsJsonObject().get("score").getAsDouble());
    }

    @Test
    void filtersAssetsTheCallerCannotRead() {
        Predicate<String> noEmbargo = path -> !path.startsWith("/content/dam/embargo/");

        JsonArray results = context.semanticSearch("snow skiing mountain", null, null, 10, noEmbargo);

        assertEquals(List.of(SKI, BEACH), List.of(path(results, 0), path(results, 1)));
        assertEquals(2, results.size());
    }

    @Test
    void boostsAssetsThatMatchAFact() {
        JsonArray results = context.semanticSearch("waves", "Subject", "https://semanticdam.com/id/tag/wknd/activity/skiing",
                10, path -> true);

        // A strong vector match can still outrank the boost; the fact matches are included and flagged.
        java.util.Map<String, Boolean> flags = new java.util.HashMap<>();
        results.forEach(item -> flags.put(item.getAsJsonObject().get("path").getAsString(),
                item.getAsJsonObject().get("matchesFilter").getAsBoolean()));
        assertEquals(Map.of(SKI, true, SECRET, true, BEACH, false), flags);
    }

    @Test
    void reportsWhenSemanticSearchIsNotConfigured() {
        context.setSemanticSearch(null);

        assertThrows(IllegalStateException.class, () -> context.semanticSearch("snow", null, null, 5, path -> true));
        McpServlet mcp = new McpServlet(context);
        JsonArray tools = mcp.dispatch(new JsonPrimitive(1), "tools/list", new JsonObject(), path -> true)
                .getAsJsonObject("result").getAsJsonArray("tools");
        assertTrue(tools.toString().indexOf("semantic_search") < 0);
    }

    @Test
    void exposesSemanticSearchOverMcpWhenConfigured() {
        McpServlet mcp = new McpServlet(context);
        JsonArray tools = mcp.dispatch(new JsonPrimitive(1), "tools/list", new JsonObject(), path -> true)
                .getAsJsonObject("result").getAsJsonArray("tools");
        assertTrue(tools.toString().contains("\"semantic_search\""));

        JsonObject params = JsonParser.parseString("{\"name\":\"semantic_search\",\"arguments\":{\"query\":\"sunny beach waves\",\"limit\":1}}")
                .getAsJsonObject();
        JsonObject result = mcp.dispatch(new JsonPrimitive(2), "tools/call", params, path -> true).getAsJsonObject("result");

        assertFalse(result.get("isError").getAsBoolean());
        assertEquals(BEACH, path(result.getAsJsonObject("structuredContent").getAsJsonArray("assets"), 0));
    }

    @Test
    void ingestJobQueuesEmbeddingAndEmbedJobsAreRetried() {
        JobManager jobs = mock(JobManager.class);
        AssetMetadataPipeline pipeline = new AssetMetadataPipeline() {
            @Override public Optional<com.semanticdam.core.core.domain.AssetMetadata> ingest(AssetId assetId) {
                return graph.findByAssetId(assetId);
            }
            @Override public void remove(AssetId assetId) { }
        };
        AssetIngestJobConsumer consumer = new AssetIngestJobConsumer(pipeline, jobs);
        consumer.process(job(SKI, "ingest"));
        verify(jobs, never()).addJob(eq(AssetIngestJobs.EMBED_TOPIC), anyMap());

        consumer.setSemanticSearch(search);
        consumer.process(job(SKI, "ingest"));
        consumer.process(job("/content/dam/site/deleted.jpg", "ingest"));
        verify(jobs).addJob(AssetIngestJobs.EMBED_TOPIC, Map.of("path", SKI, "action", "ingest"));
        verify(jobs).addJob(AssetIngestJobs.EMBED_TOPIC, Map.of("path", "/content/dam/site/deleted.jpg", "action", "remove"));

        assertEquals(JobResult.OK, search.process(job(SKI, "remove")));
        assertTrue(index.search(embeddings.embed("snow skiing"), 10).stream().noneMatch(m -> m.assetId().path().equals(SKI)));
        SemanticSearchService broken = new SemanticSearchService(input -> { throw new IllegalStateException("down"); },
                index, graph, ontology);
        assertEquals(JobResult.FAILED, broken.process(job(BEACH, "ingest")));
    }

    @Test
    void callsAnOpenAiCompatibleEmbeddingsEndpoint() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        List<String> received = new ArrayList<>();
        server.createContext("/v1/embeddings", exchange -> {
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            received.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            byte[] body = "{\"data\":[{\"embedding\":[0.5,-0.25,1.0]}],\"model\":\"m\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(exchange.getRequestURI().getQuery() == null ? 200 : 500, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort() + "/v1/embeddings";
            EmbeddingVector vector = new OpenAiCompatibleEmbeddingProvider(base, "key", "mxbai", "2", Duration.ofSeconds(2))
                    .embed("snow \"quoted\"");

            assertEquals(3, vector.dimension());
            assertEquals(-0.25, vector.valueAt(1));
            assertEquals("mxbai", vector.modelId());
            assertEquals("2", vector.modelVersion());
            assertEquals("snow \"quoted\"", JsonParser.parseString(received.get(0)).getAsJsonObject().get("input").getAsString());
            assertEquals("Bearer key", received.get(1));
            assertThrows(IllegalStateException.class, () -> new OpenAiCompatibleEmbeddingProvider(base + "?fail=1", "",
                    "mxbai", "2", Duration.ofSeconds(2)).embed("x"));
        } finally {
            server.stop(0);
        }
    }

    private static Job job(String path, String action) {
        Job job = mock(Job.class);
        when(job.getProperty("path", String.class)).thenReturn(path);
        when(job.getProperty("action", String.class)).thenReturn(action);
        return job;
    }

    private static String path(JsonArray results, int index) {
        return results.get(index).getAsJsonObject().get("path").getAsString();
    }
}
