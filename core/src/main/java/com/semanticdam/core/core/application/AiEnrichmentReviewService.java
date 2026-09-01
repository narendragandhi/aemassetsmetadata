package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetMetadata;

/** Converts an explicitly approved proposal into metadata; proposed facts never auto-index. */
public class AiEnrichmentReviewService {
    public AssetMetadata applyApproved(AiEnrichmentProposal proposal, AssetMetadata current) {
        if (proposal == null || current == null) {
            throw new IllegalArgumentException("proposal and current metadata must be provided");
        }
        if (proposal.status() != AiEnrichmentProposal.Status.APPROVED) {
            throw new IllegalStateException("only approved AI proposals may be indexed");
        }
        if (!proposal.assetId().equals(current.assetId())) {
            throw new IllegalArgumentException("proposal asset does not match current metadata");
        }
        return current.add(proposal.statement());
    }
}
