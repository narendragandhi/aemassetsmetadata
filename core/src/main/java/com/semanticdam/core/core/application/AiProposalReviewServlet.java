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

@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_POST,
        "sling.servlet.paths=/bin/aemassets/ai/review" })
public final class AiProposalReviewServlet extends SlingAllMethodsServlet {
    @Reference
    private GovernedAiEnrichmentWorkflowComponent workflow;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String path = request.getParameter("path");
        String predicate = request.getParameter("predicate");
        String object = request.getParameter("object");
        String reason = request.getParameter("reason");
        String actor = request.getRemoteUser();
        if (!AiProposalServletSupport.isDamPath(path) || predicate == null || predicate.isBlank()
                || object == null || reason == null || reason.isBlank() || actor == null
                || actor.isBlank() || "anonymous".equalsIgnoreCase(actor)) {
            AiProposalServletSupport.badRequest(response, "path, predicate, object, reason and authenticated actor are required");
            return;
        }
        boolean approve = Boolean.parseBoolean(request.getParameter("approve"));
        AiProposalServletSupport.json(response, AiProposalJson.one(workflow.review(
                AssetId.of(path), predicate, object, actor, approve, reason)));
    }
}
