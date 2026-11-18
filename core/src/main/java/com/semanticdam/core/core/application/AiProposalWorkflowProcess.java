package com.semanticdam.core.core.application;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.semanticdam.core.core.domain.AssetId;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Workflow step that asks the configured AI provider for enrichment proposals. It only creates
 * proposals for review; it never writes to the graph. Without an enabled provider the step is a
 * no-op, so it is safe to leave in an asset workflow.
 */
@Component(service = WorkflowProcess.class,
        property = "process.label=SemanticDAM: Propose AI enrichment for review")
public class AiProposalWorkflowProcess implements WorkflowProcess {
    private static final Logger LOG = LoggerFactory.getLogger(AiProposalWorkflowProcess.class);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    private volatile GovernedAiEnrichmentWorkflowComponent workflow;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap arguments)
            throws WorkflowException {
        String payload = workItem.getWorkflowData().getPayload().toString();
        int content = payload.indexOf("/jcr:content");
        String assetPath = content >= 0 ? payload.substring(0, content) : payload;
        if (!assetPath.startsWith("/content/dam/")) {
            LOG.debug("Skipping AI enrichment for non-DAM payload {}", payload);
            return;
        }
        GovernedAiEnrichmentWorkflowComponent active = workflow;
        if (active == null) {
            LOG.info("No AI enrichment provider is enabled; skipping proposals for {}", assetPath);
            return;
        }
        try {
            int count = active.propose(AssetId.of(assetPath)).size();
            LOG.info("Created {} AI enrichment proposal(s) for review on {}", count, assetPath);
        } catch (RuntimeException ex) {
            throw new WorkflowException("AI enrichment proposal failed for " + assetPath, ex);
        }
    }
}
