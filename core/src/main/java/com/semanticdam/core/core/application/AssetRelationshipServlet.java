package com.semanticdam.core.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;

/**
 * GET /bin/aemassets/related.json - finds assets by a fact.
 * Use {@code sku=}, {@code campaign=}, or {@code predicate=} (IRI or label) with {@code value=}.
 */
@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.paths=/bin/aemassets/related.json"
        }
)
public class AssetRelationshipServlet extends SlingSafeMethodsServlet {
    private static final long serialVersionUID = 1L;

    @Reference
    private transient AssetContextService contextService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String sku = request.getParameter("sku");
        String campaign = request.getParameter("campaign");
        String predicate = request.getParameter("predicate");
        String value = request.getParameter("value");
        if (sku != null && !sku.isBlank()) {
            predicate = Vocabulary.SKU;
            value = sku;
        } else if (campaign != null && !campaign.isBlank()) {
            predicate = Vocabulary.PART_OF_CAMPAIGN;
            value = campaign;
        }
        if (predicate == null || predicate.isBlank() || value == null || value.isBlank()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST,
                    "Missing search criteria (sku, campaign, or predicate and value)");
            return;
        }
        String searchPredicate = predicate;
        String searchValue = value;
        int limit = ApiSupport.boundedInt(request.getParameter("limit"), 50, 200);
        ApiSupport.handle(response, () -> {
            JsonArray assets = contextService.find(searchPredicate, searchValue, limit, ApiSupport.readAccess(request));
            JsonObject body = new JsonObject();
            body.addProperty("count", assets.size());
            body.add("assets", assets);
            ApiSupport.json(response, body.toString());
        });
    }
}
