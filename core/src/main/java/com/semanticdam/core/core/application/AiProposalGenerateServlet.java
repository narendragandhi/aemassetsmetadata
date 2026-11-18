package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Optional;

/** POST /bin/aemassets/ai/propose?path= - asks the provider for proposals. Requires metadata edit rights. */
@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_POST,
        "sling.servlet.paths=/bin/aemassets/ai/propose" })
public final class AiProposalGenerateServlet extends SlingAllMethodsServlet {
    private static final long serialVersionUID = 1L;

    @Reference
    private transient GovernedAiEnrichmentWorkflowComponent workflow;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        Optional<String> path = ApiSupport.readableAssetPath(request, response);
        if (path.isEmpty()) {
            return;
        }
        if (!ApiSupport.canEditMetadata(request, path.get())) {
            response.sendError(SlingHttpServletResponse.SC_FORBIDDEN, "Metadata edit permission is required");
            return;
        }
        ApiSupport.handle(response, () ->
                ApiSupport.json(response, AiProposalJson.list(workflow.propose(AssetId.of(path.get())))));
    }
}
