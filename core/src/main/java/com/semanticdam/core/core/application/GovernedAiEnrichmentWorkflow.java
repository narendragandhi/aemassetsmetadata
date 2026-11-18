package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.GroundedAssetContext;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import com.semanticdam.core.core.ontology.Vocabulary;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Proposal -> review -> graph projection.
 *
 * <p>Guarantees: a provider can only propose for the asset it was asked about; the statement's
 * source is set here ({@code AI:<model>}) and never taken from model output, so a model cannot
 * pass its output off as PIM or AEM data; a fact that is already in the graph or was already
 * proposed (including rejected) is not proposed again; nothing reaches the graph without an
 * approval recorded in the audit trail.</p>
 */
public final class GovernedAiEnrichmentWorkflow {
    private final AiEnrichmentProvider provider;
    private final AiProposalStore proposalStore;
    private final KnowledgeGraphService graphService;
    private final AiProposalReviewService reviewService;
    private final Clock clock;
    private final Supplier<String> ontologyVersion;
    private final Predicate<MetadataStatement> validity;

    public GovernedAiEnrichmentWorkflow(AiEnrichmentProvider provider, AiProposalStore proposalStore,
            KnowledgeGraphService graphService, Clock clock, String ontologyVersion) {
        this(provider, proposalStore, graphService, clock, () -> ontologyVersion, statement -> true);
        if (ontologyVersion == null || ontologyVersion.isBlank()) {
            throw new IllegalArgumentException("complete workflow configuration is required");
        }
    }

    /** @param validity decides whether a proposed statement conforms to the ontology */
    public GovernedAiEnrichmentWorkflow(AiEnrichmentProvider provider, AiProposalStore proposalStore,
            KnowledgeGraphService graphService, Clock clock, Supplier<String> ontologyVersion,
            Predicate<MetadataStatement> validity) {
        if (provider == null || proposalStore == null || graphService == null || clock == null
                || ontologyVersion == null || validity == null) {
            throw new IllegalArgumentException("complete workflow configuration is required");
        }
        this.provider = provider;
        this.proposalStore = proposalStore;
        this.graphService = graphService;
        this.reviewService = new AiProposalReviewService(proposalStore);
        this.clock = clock;
        this.ontologyVersion = ontologyVersion;
        this.validity = validity;
    }

    public Collection<AiEnrichmentProposal> propose(AssetId assetId) {
        if (assetId == null) throw new IllegalArgumentException("assetId must be provided");
        AssetMetadata asset = graphService.findByAssetId(assetId).orElse(AssetMetadata.empty(assetId));
        Collection<AssetMetadata> related = graphService.findRelated(assetId);
        Collection<AiEnrichmentProposal> proposals = provider.propose(
                new GroundedAssetContext(asset, related, ontologyVersion.get()));
        Collection<AiEnrichmentProposal> known = proposalStore.findByAsset(assetId);
        List<AiEnrichmentProposal> accepted = new ArrayList<>();
        for (AiEnrichmentProposal proposal : proposals) {
            if (proposal == null || !assetId.equals(proposal.assetId())) {
                throw new IllegalArgumentException("provider returned a proposal for another asset");
            }
            MetadataStatement statement = proposal.statement()
                    .withSource(Vocabulary.AI_SOURCE_PREFIX + proposal.modelId());
            statement = statement.withValid(validity.test(statement));
            MetadataStatement candidate = statement;
            boolean duplicate = asset.statements().stream().anyMatch(candidate::sameFact)
                    || known.stream().anyMatch(existing -> existing.statement().sameFact(candidate))
                    || accepted.stream().anyMatch(existing -> existing.statement().sameFact(candidate));
            if (!duplicate) {
                AiEnrichmentProposal normalised = proposal.withStatement(statement);
                proposalStore.save(normalised);
                accepted.add(normalised);
            }
        }
        return List.copyOf(accepted);
    }

    public Collection<AiEnrichmentProposal> proposals(AssetId assetId) {
        if (assetId == null) throw new IllegalArgumentException("assetId must be provided");
        return List.copyOf(proposalStore.findByAsset(assetId));
    }

    public List<AiProposalAuditEvent> auditTrail(AssetId assetId) {
        if (assetId == null) throw new IllegalArgumentException("assetId must be provided");
        return reviewService.auditEvents(assetId);
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
