package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import com.semanticdam.core.core.graph.OntologyRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.time.Clock;
import java.util.Collection;
import java.util.List;

/**
 * OSGi wiring for the governed AI workflow. The component, and with it the {@code /bin/aemassets/ai}
 * endpoints, is only active while an {@link AiEnrichmentProvider} is configured and enabled.
 */
@Component(service = GovernedAiEnrichmentWorkflowComponent.class)
public final class GovernedAiEnrichmentWorkflowComponent {
    private final GovernedAiEnrichmentWorkflow delegate;
    private final AssetMetadataPipeline pipeline;

    @Activate
    public GovernedAiEnrichmentWorkflowComponent(@Reference AiEnrichmentProvider provider,
            @Reference AiProposalStore proposalStore, @Reference KnowledgeGraphService graphService,
            @Reference OntologyRegistry ontology, @Reference MetadataValidator validator,
            @Reference AssetMetadataPipeline pipeline) {
        this.delegate = new GovernedAiEnrichmentWorkflow(provider, proposalStore, graphService,
                Clock.systemUTC(), ontology::version, validator::isValid);
        this.pipeline = pipeline;
    }

    public Collection<AiEnrichmentProposal> propose(AssetId assetId) { return delegate.propose(assetId); }
    public Collection<AiEnrichmentProposal> proposals(AssetId assetId) { return delegate.proposals(assetId); }
    public List<AiProposalAuditEvent> auditTrail(AssetId assetId) { return delegate.auditTrail(assetId); }

    public AiEnrichmentProposal review(AssetId assetId, String predicateUri, String objectValue,
            String actor, boolean approve, String reason) {
        AiEnrichmentProposal reviewed = delegate.review(assetId, predicateUri, objectValue, actor, approve, reason);
        if (reviewed.status() == AiEnrichmentProposal.Status.APPROVED) {
            // Re-run the pipeline so inference and validation see the newly approved fact.
            pipeline.ingest(assetId);
        }
        return reviewed;
    }
}
