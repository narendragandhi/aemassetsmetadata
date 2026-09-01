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
        "sling.servlet.paths=/bin/aemassets/ai/propose" })
public final class AiProposalGenerateServlet extends SlingAllMethodsServlet {
    @Reference
    private GovernedAiEnrichmentWorkflowComponent workflow;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String path = request.getParameter("path");
        if (!AiProposalServletSupport.isDamPath(path)) {
            AiProposalServletSupport.badRequest(response, "Missing or invalid path parameter");
            return;
        }
        AiProposalServletSupport.json(response, AiProposalJson.list(workflow.propose(AssetId.of(path))));
    }
}
