package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Collection;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.paths=/bin/aemassets/related.json"
        }
)
public class AssetRelationshipServlet extends SlingSafeMethodsServlet {

    private static final String SKU_PREDICATE = "https://example.com/aem-assets-ontology#sku";
    private static final String CAMPAIGN_PREDICATE = "https://example.com/aem-assets-ontology#belongsToCampaign";

    @Reference
    private KnowledgeGraphService knowledgeGraphService;

    @Reference
    private ConflictResolutionService conflictResolutionService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String sku = request.getParameter("sku");
        String campaign = request.getParameter("campaign");
        String predicate = request.getParameter("predicate");
        String value = request.getParameter("value");

        Collection<AssetMetadata> results;

        if (sku != null && !sku.isBlank()) {
            results = knowledgeGraphService.findByPredicateValue(SKU_PREDICATE, sku);
        } else if (campaign != null && !campaign.isBlank()) {
            results = knowledgeGraphService.findByPredicateValue(CAMPAIGN_PREDICATE, campaign);
        } else if (predicate != null && value != null) {
            results = knowledgeGraphService.findByPredicateValue(predicate, value);
        } else {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "Missing search criteria (sku, campaign, or predicate/value)");
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(toJson(results));
    }

    private String toJson(Collection<AssetMetadata> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"count\":").append(results.size()).append(",\"assets\":[");

        boolean firstAsset = true;
        for (AssetMetadata metadata : results) {
            if (!firstAsset) {
                builder.append(',');
            }
            builder.append("{\"assetId\":\"").append(escape(metadata.assetId().path())).append("\",\"resolvedMetadata\":[");
            
            // Return only resolved (authoritative) metadata for the related assets
            Collection<MetadataStatement> statements = conflictResolutionService.resolve(metadata);
            
            boolean firstStatement = true;
            for (MetadataStatement statement : statements) {
                if (!firstStatement) {
                    builder.append(',');
                }
                builder.append("{\"p\":\"").append(escape(statement.predicateUri()))
                       .append("\",\"o\":\"").append(escape(statement.objectValue()))
                       .append("\",\"isValid\":").append(statement.isValid()).append("}");
                firstStatement = false;
            }
            builder.append("]}");
            firstAsset = false;
        }

        builder.append("]}");
        return builder.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
