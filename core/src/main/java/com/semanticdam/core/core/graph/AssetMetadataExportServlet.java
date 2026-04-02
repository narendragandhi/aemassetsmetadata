package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.paths=/bin/aemassets/metadata.ttl"
        }
)
public class AssetMetadataExportServlet extends SlingSafeMethodsServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetMetadataExportServlet.class);

    @Reference
    private GraphExportServiceComponent exportService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String assetPath = request.getParameter("path");
        if (assetPath == null || assetPath.isBlank()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "Missing path parameter");
            return;
        }

        exportService.exportAsset(AssetId.of(assetPath))
                .ifPresentOrElse(
                        turtle -> {
                            response.setContentType("text/turtle");
                            response.setCharacterEncoding("UTF-8");
                            try {
                                response.getWriter().write(turtle);
                            } catch (IOException ex) {
                                LOGGER.warn("Failed to write turtle response", ex);
                            }
                        },
                        () -> {
                            try {
                                response.sendError(SlingHttpServletResponse.SC_NOT_FOUND, "No metadata for asset");
                            } catch (IOException ex) {
                                LOGGER.warn("Failed to send not found", ex);
                            }
                        }
                );
    }
}
