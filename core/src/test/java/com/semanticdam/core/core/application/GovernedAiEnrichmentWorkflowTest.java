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

    /** A model must not be able to pass its output off as coming from a trusted system. */
    @Test
    void overridesTheSourceClaimedByTheModel() {
        InMemoryAiProposalStore proposals = new InMemoryAiProposalStore();
        InMemoryKnowledgeGraphStore graph = new InMemoryKnowledgeGraphStore();
        AiEnrichmentProposal spoofed = AiEnrichmentProposal.proposed(ASSET,
                MetadataStatement.literal(PREDICATE, "WKND-001", "PIM", true, 0.99),
                "test-model", "prompt-v1", "claims to be PIM");
        GovernedAiEnrichmentWorkflow workflow = workflow(proposals, graph, context -> List.of(spoofed));

        assertEquals("AI:test-model", workflow.propose(ASSET).iterator().next().statement().source());
        workflow.review(ASSET, PREDICATE, "WKND-001", "steward", true, "ok");
        assertEquals("AI:test-model", graph.findByAssetId(ASSET).orElseThrow().statements().get(0).source());
    }

    @Test
    void doesNotReproposeKnownOrRejectedFacts() {
        InMemoryAiProposalStore proposals = new InMemoryAiProposalStore();
        InMemoryKnowledgeGraphStore graph = new InMemoryKnowledgeGraphStore();
        GovernedAiEnrichmentWorkflow workflow = workflow(proposals, graph, context -> List.of(proposal(), proposal()));

        assertEquals(1, workflow.propose(ASSET).size());
        workflow.review(ASSET, PREDICATE, "WKND-001", "steward", false, "wrong product");
        assertEquals(0, workflow.propose(ASSET).size());
        assertEquals(1, workflow.proposals(ASSET).size());
    }

    @Test
    void recordsEveryDecisionInTheAuditTrail() {
        InMemoryAiProposalStore proposals = new InMemoryAiProposalStore();
        GovernedAiEnrichmentWorkflow workflow = workflow(proposals, new InMemoryKnowledgeGraphStore(),
                context -> List.of(proposal()));
        workflow.propose(ASSET);

        workflow.review(ASSET, PREDICATE, "WKND-001", "steward", true, "verified against source");

        AiProposalAuditEvent event = workflow.auditTrail(ASSET).get(0);
        assertEquals("steward", event.actor());
        assertEquals(AiEnrichmentProposal.Status.APPROVED, event.status());
        assertEquals("verified against source", event.reason());
        assertEquals(Instant.parse("2026-09-01T12:00:00Z"), event.occurredAt());
    }

    @Test
    void refusesToApproveAProposalThatViolatesTheOntology() {
        InMemoryAiProposalStore proposals = new InMemoryAiProposalStore();
        InMemoryKnowledgeGraphStore graph = new InMemoryKnowledgeGraphStore();
        GovernedAiEnrichmentWorkflow workflow = new GovernedAiEnrichmentWorkflow(context -> List.of(proposal()),
                proposals, graph, Clock.systemUTC(), () -> "2.0.0", statement -> false);
        workflow.propose(ASSET);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> workflow.review(ASSET, PREDICATE, "WKND-001", "steward", true, "looks fine"));
        assertEquals(true, graph.findByAssetId(ASSET).isEmpty());
        assertEquals(true, workflow.auditTrail(ASSET).isEmpty());
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
