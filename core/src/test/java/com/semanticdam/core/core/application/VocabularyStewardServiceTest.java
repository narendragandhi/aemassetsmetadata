package com.semanticdam.core.core.application;

import com.semanticdam.core.core.TestOntology;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.InMemoryKnowledgeGraphStore;
import com.semanticdam.core.core.ontology.OntologyModel;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.apache.sling.event.jobs.JobManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.event.EventAdmin;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Candidate -> steward decision -> re-ingest, with the real pipeline. */
class VocabularyStewardServiceTest {
    private static final AssetId HERO = AssetId.of("/content/dam/site/hero.jpg");
    private static final AssetId BANNER = AssetId.of("/content/dam/site/banner.jpg");
    private static final String ACME = "https://semanticdam.com/id/brand/acme";
    private static final String ACMEE = "https://semanticdam.com/id/brand/acmee";

    private final OntologyModel ontology = TestOntology.load();
    private final InMemoryKnowledgeGraphStore graph = new InMemoryKnowledgeGraphStore();
    private final MapBackedAemMetadataSource dam = new MapBackedAemMetadataSource();
    private final JobManager jobs = mock(JobManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    private DefaultAssetMetadataPipeline pipeline;
    private VocabularyStewardService stewards;

    @BeforeEach
    void setUp() {
        MetadataValidator validator = new MetadataValidator(ontology);
        pipeline = new DefaultAssetMetadataPipeline(dam,
                new AssetMetadataExtractionService(new AemAssetMetadataMapper(ontology)),
                new AssetMetadataIndexingService(graph),
                new SemanticEventService(ontology, validator, mock(EventAdmin.class)),
                new InferenceService(ontology), validator, ontology, graph);
        stewards = new VocabularyStewardService(graph, ontology, jobs);
        dam.put(HERO, Map.of("aem:brand", "Acme"));
        dam.put(BANNER, Map.of("aem:brand", "Acmee"));
        pipeline.ingest(HERO);
        pipeline.ingest(BANNER);
    }

    @Test
    void listsCandidatesWithTheirUsage() {
        assertEquals(List.of(ACME, ACMEE), stewards.candidates(Vocabulary.BRAND_CLASS).stream().map(Entity::uri).toList());
        assertEquals(List.of(HERO), stewards.usage(ACME).stream().map(AssetMetadata::assetId).toList());
        assertTrue(stewards.candidates(Vocabulary.CAMPAIGN_CLASS).isEmpty());
    }

    @Test
    void approvalMakesLinksValidAfterReingest() {
        VocabularyStewardService.Result result = stewards.decide(ACME, VocabularyStewardService.Decision.APPROVE, null, "steward");

        assertEquals(Entity.Status.APPROVED, result.entity().status());
        assertEquals(1, result.assetsQueued());
        verify(jobs).addJob(AssetIngestJobs.TOPIC, Map.of("path", HERO.path(), "action", "ingest"));
        // a delayed second pass covers assets the asynchronous index did not show yet
        verify(jobs).createJob(VocabularyStewardService.REINGEST_TOPIC);
        assertTrue(brand(pipeline.ingest(HERO).orElseThrow()).isValid());
    }

    @Test
    void mergeRedirectsTheVariantAndRemembersItAsAHiddenLabel() {
        stewards.decide(ACME, VocabularyStewardService.Decision.APPROVE, null, "steward");

        stewards.decide(ACMEE, VocabularyStewardService.Decision.MERGE, ACME, "steward");

        assertEquals(Entity.Status.MERGED, graph.findEntity(ACMEE).orElseThrow().status());
        assertEquals(List.of("Acmee"), graph.findEntity(ACME).orElseThrow().hiddenLabels());
        MetadataStatement brand = brand(pipeline.ingest(BANNER).orElseThrow());
        assertEquals(ACME, brand.objectValue());
        assertTrue(brand.isValid());
        assertTrue(graph.findReferencing(ACMEE).isEmpty());
    }

    @Test
    void rejectionKeepsTheLinkInvalidAndGuardsBadMerges() {
        stewards.decide(ACMEE, VocabularyStewardService.Decision.REJECT, null, "steward");

        assertFalse(brand(pipeline.ingest(BANNER).orElseThrow()).isValid());
        // cannot merge into a candidate, into another class, or into itself
        assertThrows(IllegalStateException.class, () -> stewards.decide(ACMEE, VocabularyStewardService.Decision.MERGE, ACME, "s"));
        assertThrows(IllegalArgumentException.class, () -> stewards.decide(ACME, VocabularyStewardService.Decision.MERGE,
                "https://semanticdam.com/id/campaign/summer-promo-2026", "s"));
        assertThrows(IllegalArgumentException.class, () -> stewards.decide(ACME, VocabularyStewardService.Decision.MERGE, ACME, "s"));
    }

    private static MetadataStatement brand(AssetMetadata metadata) {
        return metadata.statements().stream().filter(s -> s.predicateUri().equals(Vocabulary.BRAND)).findFirst().orElseThrow();
    }
}
