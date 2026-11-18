package com.semanticdam.core.core.application;

import com.google.gson.JsonObject;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Session;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.List;

/**
 * POST /bin/aemassets/invalidate?asset=/content/dam/... - queues dispatcher invalidation of the
 * pages that show an asset's facts, e.g. after a bulk change. Requires replicate permission on the
 * asset. Only present where {@link PageCacheInvalidation} is configured (author).
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_POST,
        "sling.servlet.paths=/bin/aemassets/invalidate" })
public final class PageCacheInvalidationServlet extends SlingAllMethodsServlet {
    private static final long serialVersionUID = 1L;

    @Reference
    private transient PageCacheInvalidation invalidation;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String[] assets = request.getParameterValues("asset");
        if (assets == null || assets.length == 0) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "asset is required");
            return;
        }
        for (String asset : assets) {
            if (!ApiSupport.isDamPath(asset) || !ApiSupport.hasPermission(request, asset, "crx:replicate")) {
                response.sendError(SlingHttpServletResponse.SC_FORBIDDEN, "Replicate permission on " + asset + " is required");
                return;
            }
        }
        invalidation.queue(List.of(assets));
        JsonObject body = new JsonObject();
        body.addProperty("queued", assets.length);
        ApiSupport.json(response, body.toString());
    }
}
