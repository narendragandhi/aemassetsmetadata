package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;

import java.time.Instant;

/** Reviews proposals, persists their state, and records an audit event. */
public final class AiProposalReviewService {
    private final AiProposalStore proposalStore;
    private final java.util.Collection<AiProposalAuditEvent> auditEvents = new java.util.ArrayList<>();

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
        AiEnrichmentProposal reviewed = approve ? proposal.approve() : proposal.reject();
        proposalStore.save(reviewed);
        auditEvents.add(new AiProposalAuditEvent(reviewed.assetId(), reviewed.status(), actor, occurredAt, reason));
        return reviewed;
    }

    public synchronized java.util.List<AiProposalAuditEvent> auditEvents() {
        return java.util.List.copyOf(auditEvents);
    }
}
