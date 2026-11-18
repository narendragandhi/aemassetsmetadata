package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;

import java.util.Collection;
import java.util.List;

/** Persistence seam for proposals; implementations must not write graph facts. */
public interface AiProposalStore {
    void save(AiEnrichmentProposal proposal);

    Collection<AiEnrichmentProposal> findByAsset(AssetId assetId);

    /** Appends a review decision to the audit trail. The trail is append-only. */
    void recordAudit(AiProposalAuditEvent event, AiEnrichmentProposal proposal);

    /** Review decisions for an asset, oldest first. */
    List<AiProposalAuditEvent> auditTrail(AssetId assetId);
}
