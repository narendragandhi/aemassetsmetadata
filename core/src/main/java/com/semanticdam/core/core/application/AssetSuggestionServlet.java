package com.semanticdam.core.core.application;

import com.google.gson.JsonArray;
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

/** GET /bin/aemassets/suggestions.json?path=&limit= - assets that share an entity with the given asset. */
@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.paths=/bin/aemassets/suggestions.json"
        }
)
public class AssetSuggestionServlet extends SlingSafeMethodsServlet {
    private static final long serialVersionUID = 1L;

    @Reference
    private transient AssetContextService contextService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        Optional<String> path = ApiSupport.readableAssetPath(request, response);
        if (path.isEmpty()) {
            return;
        }
        int limit = ApiSupport.boundedInt(request.getParameter("limit"), 10, 50);
        ApiSupport.handle(response, () -> {
            JsonArray related = contextService.related(AssetId.of(path.get()), limit, ApiSupport.readAccess(request));
            JsonObject body = new JsonObject();
            body.addProperty("count", related.size());
            body.add("suggestions", related);
            ApiSupport.json(response, body.toString());
        });
    }
}
