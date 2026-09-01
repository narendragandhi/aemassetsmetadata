package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.time.Clock;
import java.util.Collection;

@Component(service = GovernedAiEnrichmentWorkflowComponent.class)
public final class GovernedAiEnrichmentWorkflowComponent {
    private final GovernedAiEnrichmentWorkflow delegate;

    @Activate
    public GovernedAiEnrichmentWorkflowComponent(@Reference AiEnrichmentProvider provider,
            @Reference AiProposalStore proposalStore, @Reference KnowledgeGraphService graphService) {
        this.delegate = new GovernedAiEnrichmentWorkflow(provider, proposalStore, graphService,
                Clock.systemUTC(), "asset-ontology-v1");
    }

    public Collection<AiEnrichmentProposal> propose(AssetId assetId) { return delegate.propose(assetId); }

    public Collection<AiEnrichmentProposal> proposals(AssetId assetId) { return delegate.proposals(assetId); }

    public AiEnrichmentProposal review(AssetId assetId, String predicateUri, String objectValue,
            String actor, boolean approve, String reason) {
        return delegate.review(assetId, predicateUri, objectValue, actor, approve, reason);
    }
}
