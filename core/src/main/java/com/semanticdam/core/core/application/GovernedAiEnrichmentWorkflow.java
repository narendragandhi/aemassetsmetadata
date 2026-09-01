package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.GroundedAssetContext;
import com.semanticdam.core.core.graph.KnowledgeGraphService;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Complete proposal -> review -> graph projection workflow. */
public final class GovernedAiEnrichmentWorkflow {
    private final AiEnrichmentProvider provider;
    private final AiProposalStore proposalStore;
    private final KnowledgeGraphService graphService;
    private final AiProposalReviewService reviewService;
    private final Clock clock;
    private final String ontologyVersion;

    public GovernedAiEnrichmentWorkflow(AiEnrichmentProvider provider, AiProposalStore proposalStore,
            KnowledgeGraphService graphService, Clock clock, String ontologyVersion) {
        if (provider == null || proposalStore == null || graphService == null || clock == null
                || ontologyVersion == null || ontologyVersion.isBlank()) {
            throw new IllegalArgumentException("complete workflow configuration is required");
        }
        this.provider = provider;
        this.proposalStore = proposalStore;
        this.graphService = graphService;
        this.reviewService = new AiProposalReviewService(proposalStore);
        this.clock = clock;
        this.ontologyVersion = ontologyVersion;
    }

    public Collection<AiEnrichmentProposal> propose(AssetId assetId) {
        if (assetId == null) throw new IllegalArgumentException("assetId must be provided");
        AssetMetadata asset = graphService.findByAssetId(assetId).orElse(AssetMetadata.empty(assetId));
        Collection<AssetMetadata> related = graphService.findRelated(assetId);
        Collection<AiEnrichmentProposal> proposals = provider.propose(
                new GroundedAssetContext(asset, related, ontologyVersion));
        for (AiEnrichmentProposal proposal : proposals) {
            if (proposal == null || !assetId.equals(proposal.assetId())) {
                throw new IllegalArgumentException("provider returned a proposal for another asset");
            }
            proposalStore.save(proposal);
        }
        return List.copyOf(proposals);
    }

    public Collection<AiEnrichmentProposal> proposals(AssetId assetId) {
        if (assetId == null) throw new IllegalArgumentException("assetId must be provided");
        return List.copyOf(proposalStore.findByAsset(assetId));
    }

    public AiEnrichmentProposal review(AssetId assetId, String predicateUri, String objectValue,
            String actor, boolean approve, String reason) {
        AiEnrichmentProposal proposal = proposalStore.findByAsset(assetId).stream()
                .filter(candidate -> candidate.status() == AiEnrichmentProposal.Status.PROPOSED)
                .filter(candidate -> candidate.statement().predicateUri().equals(predicateUri))
                .filter(candidate -> candidate.statement().objectValue().equals(objectValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("proposed fact was not found"));
        AiEnrichmentProposal reviewed = reviewService.review(proposal, actor, approve, reason, Instant.now(clock));
        if (reviewed.status() == AiEnrichmentProposal.Status.APPROVED) {
            AssetMetadata current = graphService.findByAssetId(assetId).orElse(AssetMetadata.empty(assetId));
            graphService.writeAssetMetadata(new AiEnrichmentReviewService().applyApproved(reviewed, current));
        }
        return reviewed;
    }
}
