package com.semanticdam.core.core.application;

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

/** GET /bin/aemassets/ai/proposals.json?path= - proposals and their review audit trail. */
@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.paths=/bin/aemassets/ai/proposals.json" })
public final class AiProposalQueryServlet extends SlingSafeMethodsServlet {
    private static final long serialVersionUID = 1L;

    @Reference
    private transient GovernedAiEnrichmentWorkflowComponent workflow;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        Optional<String> path = ApiSupport.readableAssetPath(request, response);
        if (path.isEmpty()) {
            return;
        }
        ApiSupport.handle(response, () -> {
            AssetId assetId = AssetId.of(path.get());
            ApiSupport.json(response, AiProposalJson.list(workflow.proposals(assetId), workflow.auditTrail(assetId)));
        });
    }
}
