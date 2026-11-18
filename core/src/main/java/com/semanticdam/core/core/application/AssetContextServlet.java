package com.semanticdam.core.core.application;

import com.google.gson.JsonObject;
import com.semanticdam.core.core.domain.AssetId;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Optional;

/**
 * GET /bin/aemassets/context.json?path=&related=10 - everything known about one asset in a single
 * document: authoritative facts with provenance, linked entities, related assets and the reason
 * they are related, shape violations and readiness.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.paths=/bin/aemassets/context.json" })
public final class AssetContextServlet extends SlingSafeMethodsServlet {
    private static final long serialVersionUID = 1L;

    @Reference
    private transient AssetContextService contextService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        Optional<String> path = ApiSupport.readableAssetPath(request, response);
        if (path.isEmpty()) {
            return;
        }
        int related = ApiSupport.boundedInt(request.getParameter("related"), 10, 50);
        ApiSupport.handle(response, () -> {
            Optional<JsonObject> context = contextService.describe(
                    AssetId.of(path.get()), related, ApiSupport.readAccess(request));
            if (context.isEmpty()) {
                response.sendError(SlingHttpServletResponse.SC_NOT_FOUND, "Asset is not in the graph yet");
                return;
            }
            ApiSupport.json(response, context.get().toString());
        });
    }
}
