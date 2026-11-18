package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Deterministic guardrail applied before provider proposals enter review. */
public final class AiEnrichmentPolicy {
    private final int maxProposals;
    private final double minimumConfidence;
    private final Set<String> allowedPredicates;

    public AiEnrichmentPolicy(int maxProposals, double minimumConfidence, Collection<String> allowedPredicates) {
        if (maxProposals <= 0 || minimumConfidence < 0.0 || minimumConfidence > 1.0
                || allowedPredicates == null) {
            throw new IllegalArgumentException("invalid AI enrichment policy");
        }
        this.maxProposals = maxProposals;
        this.minimumConfidence = minimumConfidence;
        this.allowedPredicates = Set.copyOf(new HashSet<>(allowedPredicates));
    }

    public void validate(Collection<AiEnrichmentProposal> proposals) {
        if (proposals == null || proposals.size() > maxProposals) {
            throw new IllegalArgumentException("proposal count exceeds policy limit");
        }
        for (AiEnrichmentProposal proposal : proposals) {
            if (proposal == null || proposal.statement().confidenceScore() < minimumConfidence
                    || !allowedPredicates.contains(proposal.statement().predicateUri())) {
                throw new IllegalArgumentException("proposal violates enrichment policy");
            }
        }
    }

    public Set<String> allowedPredicates() {
        return allowedPredicates;
    }

    public int maxProposals() {
        return maxProposals;
    }

    public double minimumConfidence() {
        return minimumConfidence;
    }
}
