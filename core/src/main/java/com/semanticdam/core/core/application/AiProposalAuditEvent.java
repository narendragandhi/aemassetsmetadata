package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;

import java.time.Instant;

public record AiProposalAuditEvent(AssetId assetId, AiEnrichmentProposal.Status status,
        String actor, Instant occurredAt, String reason) {
    public AiProposalAuditEvent {
        if (assetId == null || status == null || actor == null || actor.isBlank()
                || occurredAt == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("complete proposal audit data must be provided");
        }
    }
}
