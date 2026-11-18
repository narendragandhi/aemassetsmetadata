package com.semanticdam.core.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.semanticdam.core.core.TestOntology;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.InMemoryKnowledgeGraphStore;
import com.semanticdam.core.core.ontology.OntologyModel;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.event.EventAdmin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Context API and MCP endpoint over a graph that was filled by the real pipeline, so the test
 * covers the path DAM properties -> graph -> what an agent is told.
 */
class AssetContextApiTest {
    private static final String HERO = "/content/dam/site/hero.jpg";
    private static final String BANNER = "/content/dam/site/banner.jpg";
    private static final String SECRET = "/content/dam/embargo/secret.jpg";
    private static final String LOOKBOOK = "/content/dam/site/lookbook.jpg";
    private static final Predicate<String> ALL = path -> true;
    private static final Predicate<String> NO_EMBARGO = path -> !path.startsWith("/content/dam/embargo/");

    private final OntologyModel ontology = TestOntology.load();
    private final InMemoryKnowledgeGraphStore graph = new InMemoryKnowledgeGraphStore();
    private AssetContextService context;
    private McpServlet mcp;

    @BeforeEach
    void setUp() {
        MapBackedAemMetadataSource dam = new MapBackedAemMetadataSource();
        MetadataValidator validator = new MetadataValidator(ontology);
        DefaultAssetMetadataPipeline pipeline = new DefaultAssetMetadataPipeline(dam,
                new AssetMetadataExtractionService(new AemAssetMetadataMapper(ontology)),
                new AssetMetadataIndexingService(graph),
                new SemanticEventService(ontology, validator, mock(EventAdmin.class)),
                new InferenceService(ontology), validator, ontology, graph);
        graph.writeEntity(new com.semanticdam.core.core.domain.Entity("https://semanticdam.com/id/brand/acme",
                com.semanticdam.core.core.ontology.Vocabulary.BRAND_CLASS, "Acme"));
        dam.put(AssetId.of(HERO), Map.of("dc:title", "Hero", "aem:brand", "Acme", "pim:sku", "SUM-2026",
                "pim:category", "Organic Cotton", "workfront:campaignName", "SUMMER_PROMO_2026"));
        dam.put(AssetId.of(BANNER), Map.of("dc:title", "Banner", "aem:brand", "Acme", "pim:sku", "bad sku"));
        dam.put(AssetId.of(SECRET), Map.of("dc:title", "Unreleased", "aem:brand", "Acme",
                "workfront:campaignName", "SUMMER_PROMO_2026"));
        dam.put(AssetId.of(LOOKBOOK), Map.of("dc:title", "Lookbook", "workfront:campaignName", "Winter"));
        for (String path : List.of(HERO, BANNER, SECRET, LOOKBOOK)) {
            pipeline.ingest(AssetId.of(path));
        }
        ConflictResolutionServiceComponent conflicts = new ConflictResolutionServiceComponent();
        conflicts.setOntology(ontology);
        context = new AssetContextService(graph, ontology, conflicts, validator, new GraphTraversalService(graph));
        mcp = new McpServlet(context);
    }

    @Test
    void describesFactsWithProvenanceEntitiesAndReadiness() {
        JsonObject document = context.describe(AssetId.of(HERO), 10, ALL).orElseThrow();

        assertEquals("3.0.0+profile-1", document.get("ontologyVersion").getAsString());
        assertEquals("https://semanticdam.com/id/agent/AEM", fact(document, Vocabulary.BRAND).get("sourceAgent").getAsString());
        JsonObject brand = fact(document, Vocabulary.BRAND);
        assertEquals("Brand", brand.get("label").getAsString());
        assertEquals("https://semanticdam.com/id/brand/acme", brand.get("value").getAsString());
        assertEquals("Acme", brand.get("valueLabel").getAsString());
        assertEquals("AEM", brand.get("source").getAsString());
        assertEquals(0.75, brand.get("confidence").getAsDouble());
        assertTrue(brand.get("authoritative").getAsBoolean());
        assertEquals("Tier-1-Global", fact(document, "https://semanticdam.com/ontology/strategy#priority")
                .get("value").getAsString());
        assertEquals(List.of("AJO"), strings(document.getAsJsonArray("readyFor")));
        assertEquals(0, document.getAsJsonArray("violations").size());
        // brand, product, campaign, the category and its inferred broader concept
        assertEquals(5, document.getAsJsonArray("entities").size());
        // indexed at Organic Cotton; findable under Sustainable Product through the expansion
        assertEquals(List.of(HERO), strings(context.find("Subject", "Sustainable Product", 10, ALL), "path"));
    }

    @Test
    void reportsViolationsWithTheShapeMessage() {
        JsonObject document = context.describe(AssetId.of(BANNER), 10, ALL).orElseThrow();

        assertFalse(fact(document, Vocabulary.SKU).get("valid").getAsBoolean());
        List<String> messages = new ArrayList<>();
        document.getAsJsonArray("violations").forEach(item -> messages.add(item.getAsJsonObject().get("message").getAsString()));
        assertTrue(messages.contains("SKU must be upper-case letters, digits and hyphens."), messages.toString());
        assertTrue(document.getAsJsonArray("readyFor").isEmpty());
    }

    @Test
    void explainsWhyAssetsAreRelated() {
        JsonArray related = context.describe(AssetId.of(HERO), 10, ALL).orElseThrow().getAsJsonArray("related");

        JsonObject first = related.get(0).getAsJsonObject();
        assertEquals(SECRET, first.get("path").getAsString());
        assertEquals(Set.of("Brand", "Campaign"), Set.copyOf(strings(first.getAsJsonArray("via"), "label")));
        assertEquals(List.of(SECRET, BANNER), strings(related, "path"));
    }

    /** The graph is read with a service user; the caller's own access must still decide. */
    @Test
    void neverRevealsAssetsTheCallerCannotRead() {
        assertTrue(context.describe(AssetId.of(SECRET), 10, NO_EMBARGO).isEmpty());
        assertEquals(List.of(BANNER), strings(
                context.describe(AssetId.of(HERO), 10, NO_EMBARGO).orElseThrow().getAsJsonArray("related"), "path"));
        assertEquals(List.of(BANNER, HERO), strings(context.find("Brand", "Acme", 10, NO_EMBARGO), "path"));
        assertTrue(context.related(AssetId.of(SECRET), 10, NO_EMBARGO).isEmpty());
        assertTrue(context.explainPath(AssetId.of(HERO), SECRET, 6, NO_EMBARGO).isEmpty());
        assertFalse(context.describe(AssetId.of(HERO), 10, NO_EMBARGO).orElseThrow().toString().contains("embargo"));
    }

    @Test
    void findsAssetsByLabelIriOrLiteral() {
        assertEquals(3, context.find("Brand", "acme", 10, ALL).size());
        assertEquals(3, context.find(Vocabulary.BRAND, "https://semanticdam.com/id/brand/acme", 10, ALL).size());
        assertEquals(List.of(HERO), strings(context.find(Vocabulary.SKU, "SUM-2026", 10, ALL), "path"));
        // found through the inferred broader concept
        assertEquals(List.of(HERO), strings(context.find("Subject", "Sustainable Product", 10, ALL), "path"));
        assertEquals(1, context.find("Brand", "Acme", 1, ALL).size());
        assertTrue(context.find("Brand", "Nobody", 10, ALL).isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> context.find("jcr:primaryType", "dam:Asset", 10, ALL));
        assertTrue(tool("find_assets", "{\"predicate\":\"nope\",\"value\":\"x\"}", ALL).get("isError").getAsBoolean());
    }

    @Test
    void mcpInitializeNegotiatesAVersionAndAdvertisesTools() {
        JsonObject init = call("initialize", "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}}");
        assertEquals("2025-03-26", init.getAsJsonObject("result").get("protocolVersion").getAsString());
        assertTrue(init.getAsJsonObject("result").getAsJsonObject("capabilities").has("tools"));
        assertEquals("2025-06-18", call("initialize", "{\"protocolVersion\":\"1999-01-01\"}")
                .getAsJsonObject("result").get("protocolVersion").getAsString());

        JsonArray tools = call("tools/list", "{}").getAsJsonObject("result").getAsJsonArray("tools");
        assertEquals(List.of("describe_ontology", "get_asset_context", "find_assets", "related_assets",
                "explain_relationship"), strings(tools, "name"));
        for (JsonElement tool : tools) {
            JsonObject schema = tool.getAsJsonObject().getAsJsonObject("inputSchema");
            assertEquals("object", schema.get("type").getAsString());
            for (JsonElement required : schema.getAsJsonArray("required")) {
                assertTrue(schema.getAsJsonObject("properties").has(required.getAsString()));
            }
        }
    }

    @Test
    void mcpToolsReturnStructuredContentThatMatchesTheTextContent() {
        JsonObject result = tool("get_asset_context", "{\"path\":\"" + HERO + "\"}", ALL);

        assertFalse(result.get("isError").getAsBoolean());
        JsonObject structured = result.getAsJsonObject("structuredContent");
        assertEquals(HERO, structured.get("path").getAsString());
        assertEquals(structured, JsonParser.parseString(
                result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()));

        JsonObject found = tool("find_assets", "{\"predicate\":\"Campaign\",\"value\":\"SUMMER_PROMO_2026\"}", NO_EMBARGO);
        assertEquals(List.of(HERO), strings(found.getAsJsonObject("structuredContent").getAsJsonArray("assets"), "path"));

        JsonObject path = tool("explain_relationship", "{\"from\":\"" + BANNER + "\",\"to\":\"" + HERO + "\"}", ALL);
        JsonArray steps = path.getAsJsonObject("structuredContent").getAsJsonArray("path");
        assertEquals(List.of("asset", "entity", "asset"), strings(steps, "kind"));
        assertEquals("Acme", steps.get(1).getAsJsonObject().get("label").getAsString());

        JsonObject ontologyTool = tool("describe_ontology", "{}", ALL);
        assertEquals("3.0.0+profile-1", ontologyTool.getAsJsonObject("structuredContent").get("version").getAsString());
        assertTrue(ontologyTool.toString().contains("\"definition\":\"A product made or sourced with reduced environmental impact.\""));
    }

    @Test
    void mcpReportsBadInputInBandAndProtocolErrorsAsJsonRpcErrors() {
        assertTrue(tool("get_asset_context", "{\"path\":\"/etc/passwd\"}", ALL).get("isError").getAsBoolean());
        assertTrue(tool("get_asset_context", "{}", ALL).get("isError").getAsBoolean());
        assertTrue(tool("get_asset_context", "{\"path\":\"" + SECRET + "\"}", NO_EMBARGO).get("isError").getAsBoolean());

        JsonObject params = JsonParser.parseString("{\"name\":\"drop_database\",\"arguments\":{}}").getAsJsonObject();
        assertEquals(-32602, mcp.dispatch(new JsonPrimitive(1), "tools/call", params, ALL)
                .getAsJsonObject("error").get("code").getAsInt());
        assertEquals(-32601, call("resources/list", "{}").getAsJsonObject("error").get("code").getAsInt());
        assertEquals(new JsonObject(), call("ping", "{}").getAsJsonObject("result"));
    }

    private JsonObject call(String method, String params) {
        JsonObject response = mcp.dispatch(new JsonPrimitive(7), method, JsonParser.parseString(params).getAsJsonObject(), ALL);
        assertEquals("2.0", response.get("jsonrpc").getAsString());
        assertEquals(7, response.get("id").getAsInt());
        return response;
    }

    private JsonObject tool(String name, String arguments, Predicate<String> canRead) {
        JsonObject params = new JsonObject();
        params.addProperty("name", name);
        params.add("arguments", JsonParser.parseString(arguments));
        return mcp.dispatch(new JsonPrimitive(1), "tools/call", params, canRead).getAsJsonObject("result");
    }

    private static JsonObject fact(JsonObject document, String predicate) {
        for (JsonElement fact : document.getAsJsonArray("facts")) {
            if (fact.getAsJsonObject().get("predicate").getAsString().equals(predicate)) {
                return fact.getAsJsonObject();
            }
        }
        throw new AssertionError("no fact for " + predicate + " in " + document);
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.getAsString()));
        return values;
    }

    private static List<String> strings(JsonArray array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.getAsJsonObject().get(field).getAsString()));
        return values;
    }

    @Test
    void jsonLdCannotBreakOutOfAScriptElement() {
        AssetId assetId = AssetId.of("/content/dam/site/xss.jpg");
        graph.writeAssetMetadata(graph.findByAssetId(AssetId.of(HERO)).orElseThrow().add(
                MetadataStatement.literal("https://schema.org/category", "</script><script>alert(1)</script>", "AI:m")));
        ConflictResolutionServiceComponent conflicts = new ConflictResolutionServiceComponent();
        conflicts.setOntology(ontology);
        SemanticSeoService seo = new SemanticSeoService(graph, conflicts);

        String jsonLd = seo.getJsonLd(HERO);

        assertFalse(jsonLd.contains("<"), jsonLd);
        JsonObject parsed = JsonParser.parseString(jsonLd).getAsJsonObject();
        assertEquals("</script><script>alert(1)</script>", parsed.get("category").getAsString());
        assertEquals("ImageObject", parsed.get("@type").getAsString());
        assertEquals("Hero", parsed.get("name").getAsString());
        assertEquals("Acme", parsed.getAsJsonObject("brand").get("name").getAsString());
        assertEquals("Brand", parsed.getAsJsonObject("brand").get("@type").getAsString());
        assertEquals("", seo.getJsonLd(assetId.path()));
    }
}
