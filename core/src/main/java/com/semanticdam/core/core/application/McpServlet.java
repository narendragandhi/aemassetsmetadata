package com.semanticdam.core.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.graph.GraphStoreException;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

/**
 * POST /bin/aemassets/mcp - a Model Context Protocol server exposing the asset graph to agents.
 *
 * <p>Implements the stateless request/response part of the Streamable HTTP transport: one JSON-RPC
 * message per POST, answered with one JSON response. There are no sessions, server-initiated
 * messages or SSE streams, so GET answers 405 as the specification allows. The tools are read-only.
 * Callers authenticate to AEM as usual and every result is filtered by their own read access.</p>
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_POST,
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.paths=/bin/aemassets/mcp" })
public final class McpServlet extends SlingAllMethodsServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(McpServlet.class);
    private static final List<String> PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");
    private static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    @Reference
    private transient AssetContextService contextService;

    public McpServlet() {
    }

    McpServlet(AssetContextService contextService) {
        this.contextService = contextService;
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setHeader("Allow", "POST");
        response.sendError(SlingHttpServletResponse.SC_METHOD_NOT_ALLOWED, "This MCP server does not offer an SSE stream");
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        if (!sameOrigin(request)) {
            response.sendError(SlingHttpServletResponse.SC_FORBIDDEN, "Cross-origin requests are not accepted");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            response.sendError(SlingHttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request is too large");
            return;
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
        } catch (JsonParseException e) {
            ApiSupport.json(response, error(JsonNull.INSTANCE, PARSE_ERROR, "Parse error").toString());
            return;
        }
        if (!parsed.isJsonObject()) {
            // JSON-RPC batches were removed from MCP in revision 2025-06-18.
            ApiSupport.json(response, error(JsonNull.INSTANCE, INVALID_REQUEST, "A single JSON-RPC object is required").toString());
            return;
        }
        JsonObject message = parsed.getAsJsonObject();
        JsonElement id = message.get("id");
        JsonElement method = message.get("method");
        if (method == null || !method.isJsonPrimitive()) {
            if (id == null) {
                response.setStatus(SlingHttpServletResponse.SC_ACCEPTED);
            } else {
                ApiSupport.json(response, error(id, INVALID_REQUEST, "method is required").toString());
            }
            return;
        }
        if (id == null || id.isJsonNull()) {
            // A notification (for example notifications/initialized) gets no JSON-RPC response.
            response.setStatus(SlingHttpServletResponse.SC_ACCEPTED);
            return;
        }
        JsonObject params = message.has("params") && message.get("params").isJsonObject()
                ? message.getAsJsonObject("params") : new JsonObject();
        ApiSupport.json(response, dispatch(id, method.getAsString(), params, ApiSupport.readAccess(request)).toString());
    }

    JsonObject dispatch(JsonElement id, String method, JsonObject params, Predicate<String> canRead) {
        switch (method) {
            case "initialize":
                return result(id, initialize(params));
            case "ping":
                return result(id, new JsonObject());
            case "tools/list":
                JsonObject tools = new JsonObject();
                JsonArray definitions = toolDefinitions();
                if (contextService.semanticSearchAvailable()) {
                    definitions.add(tool("semantic_search", "Search assets by meaning",
                            "Finds assets whose governed facts are closest in meaning to a natural-language query, "
                                    + "optionally boosting those that also have a given fact. Use find_assets for exact matches.",
                            new String[][] {
                                    {"query", "string", "What you are looking for, in natural language", "required"},
                                    {"predicate", "string", "Optional predicate IRI or label to boost, e.g. 'Brand'"},
                                    {"value", "string", "Value for the optional predicate"},
                                    {"limit", "integer", "Maximum number of assets (default 10, max 50)"}}));
                }
                tools.add("tools", definitions);
                return result(id, tools);
            case "tools/call":
                String name = string(params, "name");
                if (name == null) {
                    return error(id, INVALID_PARAMS, "params.name is required");
                }
                JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                        ? params.getAsJsonObject("arguments") : new JsonObject();
                try {
                    return result(id, callTool(name, arguments, canRead));
                } catch (UnknownToolException e) {
                    return error(id, INVALID_PARAMS, "Unknown tool: " + name);
                } catch (IllegalArgumentException e) {
                    // Bad arguments are reported in-band so that the model can correct itself.
                    return result(id, toolResult(null, e.getMessage(), true));
                } catch (GraphStoreException e) {
                    LOG.error("MCP tool {} failed: graph store unavailable", name, e);
                    return result(id, toolResult(null, "The asset graph is temporarily unavailable.", true));
                } catch (RuntimeException e) {
                    LOG.error("MCP tool {} failed", name, e);
                    return error(id, INTERNAL_ERROR, "Internal error");
                }
            default:
                return error(id, METHOD_NOT_FOUND, "Method not found: " + method);
        }
    }

    private JsonObject initialize(JsonObject params) {
        String requested = string(params, "protocolVersion");
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion",
                requested != null && PROTOCOL_VERSIONS.contains(requested) ? requested : PROTOCOL_VERSIONS.get(0));
        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        result.add("capabilities", capabilities);
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "semanticdam");
        serverInfo.addProperty("title", "SemanticDAM asset context");
        serverInfo.addProperty("version", "1.0.0");
        result.add("serverInfo", serverInfo);
        result.addProperty("instructions", "Read-only access to the AEM asset knowledge graph. Call "
                + "describe_ontology first to learn the predicates and entity classes, then find_assets or "
                + "get_asset_context. Facts carry a source and a confidence; prefer facts marked authoritative.");
        return result;
    }

    private JsonObject callTool(String name, JsonObject arguments, Predicate<String> canRead) {
        switch (name) {
            case "describe_ontology":
                return toolResult(contextService.describeOntology(), null, false);
            case "get_asset_context": {
                String path = damPath(arguments, "path");
                return contextService.describe(AssetId.of(path), limit(arguments, 10, 50), canRead)
                        .map(context -> toolResult(context, null, false))
                        .orElseGet(() -> toolResult(null, "No asset with graph metadata at " + path, true));
            }
            case "find_assets": {
                JsonObject result = new JsonObject();
                result.add("assets", contextService.find(required(arguments, "predicate"),
                        required(arguments, "value"), limit(arguments, 20, 100), canRead));
                return toolResult(result, null, false);
            }
            case "related_assets": {
                JsonObject result = new JsonObject();
                result.add("related", contextService.related(AssetId.of(damPath(arguments, "path")),
                        limit(arguments, 10, 50), canRead));
                return toolResult(result, null, false);
            }
            case "semantic_search": {
                if (!contextService.semanticSearchAvailable()) {
                    throw new UnknownToolException();
                }
                JsonObject result = new JsonObject();
                result.add("assets", contextService.semanticSearch(required(arguments, "query"),
                        string(arguments, "predicate"), string(arguments, "value"), limit(arguments, 10, 50), canRead));
                return toolResult(result, null, false);
            }
            case "explain_relationship": {
                String from = damPath(arguments, "from");
                String to = required(arguments, "to");
                return contextService.explainPath(AssetId.of(from), to, 6, canRead)
                        .map(path -> {
                            JsonObject result = new JsonObject();
                            result.add("path", path);
                            return toolResult(result, null, false);
                        })
                        .orElseGet(() -> toolResult(null, "No connection found between " + from + " and " + to, false));
            }
            default:
                throw new UnknownToolException();
        }
    }

    private static JsonArray toolDefinitions() {
        JsonArray tools = new JsonArray();
        tools.add(tool("describe_ontology", "Describe the vocabulary",
                "Lists the classes, properties (with whether their value is a literal or an entity), taxonomy "
                        + "concepts, DAM property mappings and readiness targets of the asset ontology.",
                new String[][] {}));
        tools.add(tool("get_asset_context", "Get everything known about an asset",
                "Returns the asset's facts with source and confidence, the entities it links to, related assets "
                        + "with the reason they are related, ontology violations and what it is ready for.",
                new String[][] {
                        {"path", "string", "Asset path below /content/dam", "required"},
                        {"limit", "integer", "Maximum number of related assets (default 10, max 50)"}}));
        tools.add(tool("find_assets", "Find assets by a fact",
                "Finds assets with a given value for a predicate. For entity-valued predicates such as Brand or "
                        + "Campaign the value may be the entity's label.",
                new String[][] {
                        {"predicate", "string", "Predicate IRI or label, for example 'Brand' or https://schema.org/sku", "required"},
                        {"value", "string", "Literal value, entity label or entity IRI", "required"},
                        {"limit", "integer", "Maximum number of assets (default 20, max 100)"}}));
        tools.add(tool("related_assets", "List related assets",
                "Lists assets that share at least one entity (brand, product, campaign, concept) with the asset.",
                new String[][] {
                        {"path", "string", "Asset path below /content/dam", "required"},
                        {"limit", "integer", "Maximum number of assets (default 10, max 50)"}}));
        tools.add(tool("explain_relationship", "Explain how two things are connected",
                "Finds the shortest chain of shared entities between an asset and another asset or an entity.",
                new String[][] {
                        {"from", "string", "Asset path below /content/dam", "required"},
                        {"to", "string", "Another asset path or an entity IRI", "required"}}));
        return tools;
    }

    private static JsonObject tool(String name, String title, String description, String[][] parameters) {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", name);
        tool.addProperty("title", title);
        tool.addProperty("description", description);
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (String[] parameter : parameters) {
            JsonObject property = new JsonObject();
            property.addProperty("type", parameter[1]);
            property.addProperty("description", parameter[2]);
            properties.add(parameter[0], property);
            if (parameter.length > 3) {
                required.add(parameter[0]);
            }
        }
        schema.add("properties", properties);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        tool.add("inputSchema", schema);
        JsonObject annotations = new JsonObject();
        annotations.addProperty("readOnlyHint", true);
        tool.add("annotations", annotations);
        return tool;
    }

    private static JsonObject toolResult(JsonObject structured, String message, boolean isError) {
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();
        JsonObject text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", structured != null ? structured.toString() : message);
        content.add(text);
        result.add("content", content);
        if (structured != null) {
            result.add("structuredContent", structured);
        }
        result.addProperty("isError", isError);
        return result;
    }

    private static String damPath(JsonObject arguments, String name) {
        String path = required(arguments, name);
        if (!ApiSupport.isDamPath(path)) {
            throw new IllegalArgumentException(name + " must be an asset path below /content/dam");
        }
        return path;
    }

    private static String required(JsonObject arguments, String name) {
        String value = string(arguments, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Argument '" + name + "' is required");
        }
        return value;
    }

    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static int limit(JsonObject arguments, int fallback, int max) {
        return ApiSupport.boundedInt(string(arguments, "limit"), fallback, max);
    }

    private static JsonObject result(JsonElement id, JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result);
        return response;
    }

    private static JsonObject error(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);
        return response;
    }

    /** Rejects browser requests from other sites (DNS rebinding and cross-site POSTs). */
    private static boolean sameOrigin(SlingHttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(origin);
            int port = uri.getPort() > 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            return request.getServerName().equalsIgnoreCase(uri.getHost()) && request.getServerPort() == port;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static final class UnknownToolException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
