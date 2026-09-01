package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reference store for tests and demos; replace with a JCR-backed adapter. */
public final class InMemoryAiProposalStore implements AiProposalStore {
    private final Map<AssetId, Collection<AiEnrichmentProposal>> proposals = new HashMap<>();

    @Override
    public synchronized void save(AiEnrichmentProposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal must be provided");
        }
        Collection<AiEnrichmentProposal> assetProposals = proposals.computeIfAbsent(
                proposal.assetId(), ignored -> new ArrayList<>());
        assetProposals.removeIf(existing -> sameProposal(existing, proposal));
        assetProposals.add(proposal);
    }

    @Override
    public synchronized Collection<AiEnrichmentProposal> findByAsset(AssetId assetId) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        return List.copyOf(proposals.getOrDefault(assetId, List.of()));
    }

    private boolean sameProposal(AiEnrichmentProposal left, AiEnrichmentProposal right) {
        return left.statement().equals(right.statement())
                && left.modelId().equals(right.modelId())
                && left.promptVersion().equals(right.promptVersion())
                && left.rationale().equals(right.rationale());
    }
}
