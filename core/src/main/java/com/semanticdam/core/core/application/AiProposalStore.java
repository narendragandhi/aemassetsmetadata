package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;

import java.util.Collection;

/** Persistence seam for proposals; implementations must not write graph facts. */
public interface AiProposalStore {
    void save(AiEnrichmentProposal proposal);

    Collection<AiEnrichmentProposal> findByAsset(AssetId assetId);
}
