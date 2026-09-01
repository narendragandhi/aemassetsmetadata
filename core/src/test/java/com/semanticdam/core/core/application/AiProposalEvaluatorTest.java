package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiProposalEvaluatorTest {
    @Test
    void calculatesExactMatchQuality() {
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");
        MetadataStatement expected = MetadataStatement.literal("https://schema.org/category", "Outdoor");
        AiEnrichmentProposal correct = AiEnrichmentProposal.proposed(assetId, expected,
                "model-v1", "prompt-v1", "evidence");
        AiEnrichmentProposal incorrect = AiEnrichmentProposal.proposed(assetId,
                MetadataStatement.literal("https://schema.org/category", "Indoor"),
                "model-v1", "prompt-v1", "evidence");

        AiProposalEvaluator.EvaluationSummary result = new AiProposalEvaluator()
                .evaluate(List.of(correct, incorrect), List.of(expected));

        assertEquals(1, result.truePositives());
        assertEquals(1, result.falsePositives());
        assertEquals(0, result.falseNegatives());
        assertEquals(0.5, result.precision());
        assertEquals(1.0, result.recall());
    }
}
