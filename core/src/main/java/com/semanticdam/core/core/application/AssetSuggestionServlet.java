package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
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
import java.util.stream.Collectors;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.paths=/bin/aemassets/suggestions.json"
        }
)
public class AssetSuggestionServlet extends SlingSafeMethodsServlet {

    @Reference
    private KnowledgeGraphService knowledgeGraphService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String path = request.getParameter("path");
        if (path == null || path.isBlank()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "Missing asset path");
            return;
        }

        Collection<AssetMetadata> related = knowledgeGraphService.findRelated(AssetId.of(path));

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(toJson(related));
    }

    private String toJson(Collection<AssetMetadata> related) {
        String assetsJson = related.stream()
                .map(metadata -> String.format("{\"path\":\"%s\"}", escape(metadata.assetId().path())))
                .collect(Collectors.joining(","));
        
        return String.format("{\"count\":%d,\"suggestions\":[%s]}", related.size(), assetsJson);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
