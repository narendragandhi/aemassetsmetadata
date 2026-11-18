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

/**
 * POST /bin/aemassets/ai/review - approves or rejects one proposal. The reviewer must be
 * authenticated and allowed to edit the asset's metadata; their user id goes into the audit trail.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_POST,
        "sling.servlet.paths=/bin/aemassets/ai/review" })
public final class AiProposalReviewServlet extends SlingAllMethodsServlet {
    private static final long serialVersionUID = 1L;

    @Reference
    private transient GovernedAiEnrichmentWorkflowComponent workflow;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        Optional<String> path = ApiSupport.readableAssetPath(request, response);
        if (path.isEmpty()) {
            return;
        }
        String predicate = request.getParameter("predicate");
        String object = request.getParameter("object");
        String reason = request.getParameter("reason");
        String approve = request.getParameter("approve");
        if (predicate == null || predicate.isBlank() || object == null || reason == null || reason.isBlank()
                || !("true".equals(approve) || "false".equals(approve))) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST,
                    "predicate, object, reason and approve=true|false are required");
            return;
        }
        if (ApiSupport.isAnonymous(request) || !ApiSupport.canEditMetadata(request, path.get())) {
            response.sendError(SlingHttpServletResponse.SC_FORBIDDEN, "Metadata edit permission is required");
            return;
        }
        ApiSupport.handle(response, () -> ApiSupport.json(response, AiProposalJson.one(workflow.review(
                AssetId.of(path.get()), predicate, object, request.getRemoteUser(),
                Boolean.parseBoolean(approve), reason))));
    }
}
