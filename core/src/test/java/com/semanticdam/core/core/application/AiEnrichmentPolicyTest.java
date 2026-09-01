package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiEnrichmentPolicyTest {
    @Test
    void enforcesConfidencePredicateAndCount() {
        String predicate = "https://example.com/aem-assets-ontology#brand";
        AiEnrichmentProposal proposal = AiEnrichmentProposal.proposed(
                AssetId.of("/content/dam/a.jpg"),
                MetadataStatement.literal(predicate, "Example", "model", true, 0.9),
                "model", "prompt-1", "visible logo");
        AiEnrichmentPolicy policy = new AiEnrichmentPolicy(1, 0.8, List.of(predicate));

        assertDoesNotThrow(() -> policy.validate(List.of(proposal)));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validate(List.of(proposal, proposal)));
        assertThrows(IllegalArgumentException.class,
                () -> new AiEnrichmentPolicy(1, 0.95, List.of(predicate)).validate(List.of(proposal)));
        assertThrows(IllegalArgumentException.class,
                () -> new AiEnrichmentPolicy(1, 0.8, List.of("other")).validate(List.of(proposal)));
    }
}
