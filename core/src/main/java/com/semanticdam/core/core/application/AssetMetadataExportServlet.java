package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.graph.GraphExportServiceComponent;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Optional;

/** GET /bin/aemassets/metadata.ttl?path= - the asset's statements and linked entities as Turtle. */
@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.paths=/bin/aemassets/metadata.ttl"
        }
)
public class AssetMetadataExportServlet extends SlingSafeMethodsServlet {
    private static final long serialVersionUID = 1L;

    @Reference
    private transient GraphExportServiceComponent exportService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        Optional<String> path = ApiSupport.readableAssetPath(request, response);
        if (path.isEmpty()) {
            return;
        }
        ApiSupport.handle(response, () -> {
            Optional<String> turtle = exportService.exportAsset(AssetId.of(path.get()));
            if (turtle.isEmpty()) {
                response.sendError(SlingHttpServletResponse.SC_NOT_FOUND, "No metadata for asset");
                return;
            }
            response.setContentType("text/turtle");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(turtle.get());
        });
    }
}
