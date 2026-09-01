package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiEnrichmentReviewServiceTest {
    @Test
    void refusesToIndexUnapprovedProposal() {
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");
        AssetMetadata current = AssetMetadata.empty(assetId);
        AiEnrichmentProposal proposal = AiEnrichmentProposal.proposed(assetId,
                MetadataStatement.literal("https://schema.org/category", "Outdoor", "AI:model", true, 0.91),
                "model-v1", "prompt-v1", "visual evidence");

        assertThrows(IllegalStateException.class,
                () -> new AiEnrichmentReviewService().applyApproved(proposal, current));
        assertEquals(0, current.statements().size());
    }

    @Test
    void appliesOnlyApprovedProposalToMatchingAsset() {
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");
        AiEnrichmentProposal proposal = AiEnrichmentProposal.proposed(assetId,
                MetadataStatement.literal("https://schema.org/category", "Outdoor", "AI:model", true, 0.91),
                "model-v1", "prompt-v1", "visual evidence").approve();

        AssetMetadata result = new AiEnrichmentReviewService().applyApproved(proposal, AssetMetadata.empty(assetId));

        assertEquals("Outdoor", result.statements().get(0).objectValue());
    }
}
