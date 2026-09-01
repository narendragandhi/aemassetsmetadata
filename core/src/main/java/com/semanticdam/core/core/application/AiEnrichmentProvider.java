package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.GroundedAssetContext;

import java.util.Collection;

/** Provider-neutral seam for Adobe, hosted, or local model implementations. */
public interface AiEnrichmentProvider {
    Collection<AiEnrichmentProposal> propose(GroundedAssetContext context);

    /** Stable configuration key; implementations should override this. */
    default String providerId() {
        return getClass().getName();
    }
}
