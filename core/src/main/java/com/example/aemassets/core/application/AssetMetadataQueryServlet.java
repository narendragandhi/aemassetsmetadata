package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import com.example.aemassets.core.domain.MetadataStatement;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Optional;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.paths=/bin/aemassets/metadata.json"
        }
)
public class AssetMetadataQueryServlet extends SlingSafeMethodsServlet {
    @Reference
    private AssetMetadataQueryServiceComponent queryService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String assetPath = request.getParameter("path");
        if (assetPath == null || assetPath.isBlank()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "Missing path parameter");
            return;
        }

        Optional<AssetMetadata> metadata = queryService.find(AssetId.of(assetPath));
        if (metadata.isEmpty()) {
            response.sendError(SlingHttpServletResponse.SC_NOT_FOUND, "No metadata for asset");
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(toJson(metadata.get()));
    }

    private String toJson(AssetMetadata metadata) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"assetId\":\"")
                .append(escape(metadata.assetId().path()))
                .append("\",\"statements\":[");

        boolean first = true;
        for (MetadataStatement statement : metadata.statements()) {
            if (!first) {
                builder.append(',');
            }
            builder.append("{\"predicate\":\"")
                    .append(escape(statement.predicateUri()))
                    .append("\",\"object\":\"")
                    .append(escape(statement.objectValue()))
                    .append("\",\"objectIsUri\":")
                    .append(statement.objectIsUri())
                    .append('}');
            first = false;
        }
        builder.append("]}");
        return builder.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
