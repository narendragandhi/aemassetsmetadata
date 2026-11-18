package com.semanticdam.core.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;

/**
 * GET /bin/aemassets/search.json?q=&limit=&predicate=&value= - assets closest in meaning to a query.
 * Answers 409 while semantic search is not configured.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.paths=/bin/aemassets/search.json" })
public final class AssetSemanticSearchServlet extends SlingSafeMethodsServlet {
    private static final long serialVersionUID = 1L;

    @Reference
    private transient AssetContextService contextService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String query = request.getParameter("q");
        if (query == null || query.isBlank() || query.length() > 1000) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "q is required (at most 1000 characters)");
            return;
        }
        int limit = ApiSupport.boundedInt(request.getParameter("limit"), 10, 50);
        ApiSupport.handle(response, () -> {
            JsonArray assets = contextService.semanticSearch(query, request.getParameter("predicate"),
                    request.getParameter("value"), limit, ApiSupport.readAccess(request));
            JsonObject body = new JsonObject();
            body.addProperty("count", assets.size());
            body.add("assets", assets);
            ApiSupport.json(response, body.toString());
        });
    }
}
