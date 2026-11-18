package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
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

    @Reference
    private ConflictResolutionService conflictResolutionService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        java.util.Optional<String> readable = ApiSupport.readableAssetPath(request, response);
        if (readable.isEmpty()) {
            return;
        }
        boolean resolved = Boolean.parseBoolean(request.getParameter("resolved"));
        ApiSupport.handle(response, () -> {
            Optional<AssetMetadata> metadata = queryService.find(AssetId.of(readable.get()));
            if (metadata.isEmpty()) {
                response.sendError(SlingHttpServletResponse.SC_NOT_FOUND, "No metadata for asset");
                return;
            }
            ApiSupport.json(response, toJson(metadata.get(), resolved));
        });
    }

    private String toJson(AssetMetadata metadata, boolean resolved) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"assetId\":\"")
                .append(escape(metadata.assetId().path()))
                .append("\",\"isAuthoritativeView\":")
                .append(resolved)
                .append(",\"statements\":[");

        java.util.Collection<MetadataStatement> statements = resolved 
                ? conflictResolutionService.resolve(metadata) 
                : metadata.statements();

        boolean first = true;
        for (MetadataStatement statement : statements) {
            if (!first) {
                builder.append(',');
            }
            builder.append("{\"predicate\":\"")
                    .append(escape(statement.predicateUri()))
                    .append("\",\"object\":\"")
                    .append(escape(statement.objectValue()))
                    .append("\",\"objectIsUri\":")
                    .append(statement.objectIsUri())
                    .append(",\"source\":\"")
                    .append(escape(statement.source()))
                    .append("\",\"confidence\":")
                    .append(statement.confidenceScore())
                    .append(",\"isValid\":")
                    .append(statement.isValid())
                    .append("}");
            first = false;
        }
        builder.append("]}");
        return builder.toString();
    }

    private String escape(String value) {
        return JsonEscaper.escape(value);
    }
}
