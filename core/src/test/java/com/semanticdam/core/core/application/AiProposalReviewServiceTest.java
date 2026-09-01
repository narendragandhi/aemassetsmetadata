package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiProposalReviewServiceTest {
    @Test
    void persistsDecisionAndAuditWithoutApplyingMetadata() {
        InMemoryAiProposalStore store = new InMemoryAiProposalStore();
        AiProposalReviewService service = new AiProposalReviewService(store);
        AssetId assetId = AssetId.of("/content/dam/a.jpg");
        AiEnrichmentProposal proposal = AiEnrichmentProposal.proposed(assetId,
                MetadataStatement.literal("https://example.com/aem-assets-ontology#brand", "Example",
                        "vision", true, 0.95), "vision-model", "prompt-3", "logo evidence");

        AiEnrichmentProposal reviewed = service.review(proposal, "steward@example.com", true,
                "approved after visual review", Instant.parse("2026-08-24T20:00:00Z"));

        assertEquals(AiEnrichmentProposal.Status.APPROVED, reviewed.status());
        assertEquals(1, store.findByAsset(assetId).size());
        assertEquals("steward@example.com", service.auditEvents().get(0).actor());
        assertThrows(IllegalStateException.class, () -> service.review(reviewed, "steward@example.com",
                false, "cannot change decision", Instant.now()));
    }
}
