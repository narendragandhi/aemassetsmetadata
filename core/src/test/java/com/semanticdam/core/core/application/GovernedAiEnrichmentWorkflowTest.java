package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.InMemoryKnowledgeGraphStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GovernedAiEnrichmentWorkflowTest {
    private static final AssetId ASSET = AssetId.of("/content/dam/wknd/hero.jpg");
    private static final String PREDICATE = "https://example.com/ontology#sku";

    @Test
    void proposesWithoutWritingGraphAndApprovalProjectsFact() {
        InMemoryAiProposalStore proposals = new InMemoryAiProposalStore();
        InMemoryKnowledgeGraphStore graph = new InMemoryKnowledgeGraphStore();
        AiEnrichmentProposal proposal = proposal();
        GovernedAiEnrichmentWorkflow workflow = workflow(proposals, graph, context -> List.of(proposal));

        workflow.propose(ASSET);
        assertEquals(0, graph.findByAssetId(ASSET).orElse(AssetMetadata.empty(ASSET)).statements().size());
        assertEquals(AiEnrichmentProposal.Status.PROPOSED, workflow.proposals(ASSET).iterator().next().status());

        workflow.review(ASSET, PREDICATE, "WKND-001", "steward", true, "verified against source");
        assertEquals(1, graph.findByAssetId(ASSET).orElseThrow().statements().size());
        assertEquals(AiEnrichmentProposal.Status.APPROVED, workflow.proposals(ASSET).iterator().next().status());
    }

    @Test
    void rejectionNeverProjectsFact() {
        InMemoryAiProposalStore proposals = new InMemoryAiProposalStore();
        InMemoryKnowledgeGraphStore graph = new InMemoryKnowledgeGraphStore();
        GovernedAiEnrichmentWorkflow workflow = workflow(proposals, graph, context -> List.of(proposal()));

        workflow.propose(ASSET);
        workflow.review(ASSET, PREDICATE, "WKND-001", "steward", false, "not supported");
        assertEquals(0, graph.findByAssetId(ASSET).orElse(AssetMetadata.empty(ASSET)).statements().size());
    }

    private GovernedAiEnrichmentWorkflow workflow(InMemoryAiProposalStore proposals,
            InMemoryKnowledgeGraphStore graph, AiEnrichmentProvider provider) {
        return new GovernedAiEnrichmentWorkflow(provider, proposals, graph,
                Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC), "asset-ontology-v1");
    }

    private AiEnrichmentProposal proposal() {
        return AiEnrichmentProposal.proposed(ASSET,
                MetadataStatement.literal(PREDICATE, "WKND-001", "vision", true, 0.93),
                "test-model", "prompt-v1", "visible product label");
    }
}
