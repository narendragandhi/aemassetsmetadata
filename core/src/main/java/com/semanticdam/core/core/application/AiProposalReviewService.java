package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;

import java.time.Instant;
import java.util.List;

/** Reviews proposals, persists their state, and appends the decision to the store's audit trail. */
public final class AiProposalReviewService {
    private final AiProposalStore proposalStore;

    public AiProposalReviewService(AiProposalStore proposalStore) {
        if (proposalStore == null) {
            throw new IllegalArgumentException("proposalStore must be provided");
        }
        this.proposalStore = proposalStore;
    }

    public synchronized AiEnrichmentProposal review(AiEnrichmentProposal proposal, String actor,
            boolean approve, String reason, Instant occurredAt) {
        if (proposal == null || actor == null || actor.isBlank() || reason == null || reason.isBlank()
                || occurredAt == null) {
            throw new IllegalArgumentException("complete review data must be provided");
        }
        if (approve && !proposal.statement().isValid()) {
            throw new IllegalStateException("a proposal that violates the ontology cannot be approved");
        }
        AiEnrichmentProposal reviewed = approve ? proposal.approve() : proposal.reject();
        // Audit first: a decision that cannot be recorded must not take effect.
        proposalStore.recordAudit(
                new AiProposalAuditEvent(reviewed.assetId(), reviewed.status(), actor, occurredAt, reason), reviewed);
        proposalStore.save(reviewed);
        return reviewed;
    }

    public List<AiProposalAuditEvent> auditEvents(com.semanticdam.core.core.domain.AssetId assetId) {
        return proposalStore.auditTrail(assetId);
    }
}
