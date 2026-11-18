package com.semanticdam.core.core.application;

import com.semanticdam.core.core.TestOntology;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.InMemoryKnowledgeGraphStore;
import com.semanticdam.core.core.ontology.OntologyModel;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real pipeline, ontology and in-memory graph together; only the DAM is replaced. */
class DefaultAssetMetadataPipelineTest {
    private static final AssetId ASSET = AssetId.of("/content/dam/site/hero.png");
    private static final String PRIORITY = "https://semanticdam.com/ontology/strategy#priority";
    private static final String SUSTAINABLE = "https://semanticdam.com/taxonomy/sustainability#SustainableProduct";

    private final OntologyModel ontology = TestOntology.load();
    private final InMemoryKnowledgeGraphStore graph = new InMemoryKnowledgeGraphStore();
    private final MapBackedAemMetadataSource dam = new MapBackedAemMetadataSource();
    private final List<Event> events = new ArrayList<>();
    private DefaultAssetMetadataPipeline pipeline;

    @BeforeEach
    void setUp() {
        MetadataValidator validator = new MetadataValidator(ontology);
        EventAdmin eventAdmin = new EventAdmin() {
            @Override public void postEvent(Event event) { events.add(event); }
            @Override public void sendEvent(Event event) { events.add(event); }
        };
        TagResolver tags = ids -> Map.of(
                "wknd:activity/skiing", new TagResolver.TagInfo("wknd:activity/skiing", "Skiing", "wknd:activity"),
                "wknd:activity", new TagResolver.TagInfo("wknd:activity", "Activity", null));
        pipeline = new DefaultAssetMetadataPipeline(dam,
                new AssetMetadataExtractionService(new AemAssetMetadataMapper(ontology), tags),
                new AssetMetadataIndexingService(graph),
                new SemanticEventService(ontology, validator, eventAdmin),
                new InferenceService(ontology), validator, ontology, graph);
    }

    private void approveBrand(String label) {
        graph.writeEntity(new com.semanticdam.core.core.domain.Entity(
                "https://semanticdam.com/id/brand/" + label.toLowerCase(java.util.Locale.ROOT), Vocabulary.BRAND_CLASS, label));
    }

    @Test
    void storesStatementsAndEntityNodes() {
        approveBrand("Acme");
        dam.put(ASSET, Map.of("dc:title", "Hero", "aem:brand", "Acme", "pim:sku", "SUM-2026"));

        AssetMetadata stored = pipeline.ingest(ASSET).orElseThrow();

        assertEquals(stored, graph.findByAssetId(ASSET).orElseThrow());
        assertEquals("Acme", graph.findEntity("https://semanticdam.com/id/brand/acme").orElseThrow().label());
        assertEquals("SUM-2026", graph.findEntity("https://semanticdam.com/id/product/sum-2026").orElseThrow().label());
        assertTrue(stored.statements().stream().allMatch(MetadataStatement::isValid));
    }

    @Test
    void flagsInvalidValuesAndInvalidEntitiesButKeepsThem() {
        dam.put(ASSET, Map.of("pim:sku", "bad sku", "aem:brand", "Acme 2000", "dc:title", "Hero"));

        AssetMetadata stored = pipeline.ingest(ASSET).orElseThrow();

        assertFalse(value(stored, Vocabulary.SKU).isValid());
        // "Acme 2000" breaks the brand shape (letters and spaces only), so the link is invalid too
        assertFalse(value(stored, Vocabulary.BRAND).isValid());
        assertFalse(value(stored, Vocabulary.DEPICTS_PRODUCT).isValid());
        assertTrue(value(stored, Vocabulary.TITLE).isValid());
    }

    @Test
    void infersBroaderConceptsFromTheTaxonomy() {
        dam.put(ASSET, Map.of("pim:category", "Organic Cotton"));

        AssetMetadata stored = pipeline.ingest(ASSET).orElseThrow();

        MetadataStatement inferred = stored.statements().stream()
                .filter(statement -> statement.objectValue().equals(SUSTAINABLE)).findFirst().orElseThrow();
        // indexed at the specific concept; the ancestor goes under the expansion predicate
        assertEquals(Vocabulary.BROADER_SUBJECT, inferred.predicateUri());
        assertEquals(1, stored.statements().stream().filter(s -> s.predicateUri().equals(Vocabulary.SUBJECT)).count());
        assertEquals(Vocabulary.INFERENCE_SOURCE, inferred.source());
        assertEquals("Sustainable Product", graph.findEntity(SUSTAINABLE).orElseThrow().label());
        assertEquals(1, graph.findReferencing(SUSTAINABLE).size());
    }

    @Test
    void appliesDeclarativeRulesAndRetractsThemWhenTheCauseDisappears() {
        dam.put(ASSET, Map.of("workfront:campaignName", "SUMMER_PROMO_2026"));
        assertEquals("Tier-1-Global", value(pipeline.ingest(ASSET).orElseThrow(), PRIORITY).objectValue());

        dam.put(ASSET, Map.of("workfront:campaignName", "Autumn Clearance"));
        AssetMetadata updated = pipeline.ingest(ASSET).orElseThrow();

        assertTrue(updated.statements().stream().noneMatch(statement -> statement.predicateUri().equals(PRIORITY)));
    }

    @Test
    void doesNotInferFromInvalidStatements() {
        dam.put(ASSET, Map.of("workfront:campaignName", "SUMMER_PROMO_2026"));
        AssetMetadata base = pipeline.ingest(ASSET).orElseThrow();
        MetadataStatement campaign = value(base, Vocabulary.PART_OF_CAMPAIGN).withValid(false);

        AssetMetadata inferred = new InferenceService(ontology).applyInferences(
                new AssetMetadata(ASSET, List.of(campaign)), graph::findEntity);

        assertEquals(1, inferred.statements().size());
    }

    /** The defect this replaces: a metadata edit used to erase reviewed AI facts. */
    @Test
    void reingestKeepsStatementsThePipelineDoesNotOwn() {
        dam.put(ASSET, Map.of("dc:title", "Hero"));
        pipeline.ingest(ASSET);
        MetadataStatement approved = MetadataStatement.literal("https://schema.org/category", "Summer apparel",
                "AI:vision-model", true, 0.9);
        graph.writeAssetMetadata(graph.findByAssetId(ASSET).orElseThrow().add(approved));

        dam.put(ASSET, Map.of("dc:title", "Hero v2"));
        AssetMetadata stored = pipeline.ingest(ASSET).orElseThrow();

        assertTrue(stored.statements().contains(approved));
        assertEquals("Hero v2", value(stored, Vocabulary.TITLE).objectValue());
        assertEquals(2, stored.statements().size());
    }

    @Test
    void repeatedIngestIsIdempotent() {
        dam.put(ASSET, Map.of("dc:title", "Hero", "pim:category", "Organic", "workfront:campaignName", "SUMMER_PROMO_2026"));

        AssetMetadata first = pipeline.ingest(ASSET).orElseThrow();
        AssetMetadata second = pipeline.ingest(ASSET).orElseThrow();

        assertEquals(first, second);
    }

    @Test
    void removesAssetsThatNoLongerExist() {
        dam.put(ASSET, Map.of("dc:title", "Hero"));
        pipeline.ingest(ASSET);

        Optional<AssetMetadata> result = pipeline.ingest(AssetId.of("/content/dam/site/deleted.png"));
        assertTrue(result.isEmpty());
        assertTrue(graph.findByAssetId(ASSET).isPresent());

        pipeline.remove(ASSET);
        assertTrue(graph.findByAssetId(ASSET).isEmpty());
    }

    @Test
    void doesNotTouchTheGraphWhenTheDamCannotBeRead() {
        dam.put(ASSET, Map.of("dc:title", "Hero"));
        AssetMetadata before = pipeline.ingest(ASSET).orElseThrow();
        AemMetadataSource failing = assetId -> { throw new IllegalStateException("repository unavailable"); };
        MetadataValidator validator = new MetadataValidator(ontology);
        DefaultAssetMetadataPipeline broken = new DefaultAssetMetadataPipeline(failing,
                new AssetMetadataExtractionService(new AemAssetMetadataMapper(ontology)),
                new AssetMetadataIndexingService(graph),
                new SemanticEventService(ontology, validator, org.mockito.Mockito.mock(EventAdmin.class)),
                new InferenceService(ontology), validator, ontology, graph);

        assertThrows(IllegalStateException.class, () -> broken.ingest(ASSET));
        assertEquals(before, graph.findByAssetId(ASSET).orElseThrow());
    }

    @Test
    void raisesReadinessEventOnceOnTheTransition() {
        dam.put(ASSET, Map.of("pim:sku", "SUM-2026"));
        pipeline.ingest(ASSET);
        assertTrue(events.isEmpty());

        dam.put(ASSET, Map.of("pim:sku", "SUM-2026", "workfront:campaignName", "Summer"));
        pipeline.ingest(ASSET);
        pipeline.ingest(ASSET);

        assertEquals(1, events.size());
        assertEquals(SemanticEventService.TOPIC_READY, events.get(0).getTopic());
        assertEquals(ASSET.path(), events.get(0).getProperty("path"));
        assertEquals("AJO", events.get(0).getProperty("consumer"));
    }

    @Test
    void invalidSkuDoesNotMakeAnAssetReady() {
        dam.put(ASSET, Map.of("pim:sku", "bad sku", "workfront:campaignName", "Summer"));

        pipeline.ingest(ASSET);

        assertTrue(events.isEmpty());
    }

    @Test
    void infersParentTagsAndStoresTheTagHierarchy() {
        dam.put(ASSET, Map.of("cq:tags", new String[] {"wknd:activity/skiing"}));
        String skiing = "https://semanticdam.com/id/tag/wknd/activity/skiing";
        String activity = "https://semanticdam.com/id/tag/wknd/activity";

        AssetMetadata stored = pipeline.ingest(ASSET).orElseThrow();

        MetadataStatement inferred = stored.statements().stream()
                .filter(statement -> statement.objectValue().equals(activity)).findFirst().orElseThrow();
        assertEquals(Vocabulary.INFERENCE_SOURCE, inferred.source());
        assertEquals(Vocabulary.BROADER_SUBJECT, inferred.predicateUri());
        assertEquals(List.of(activity), graph.findEntity(skiing).orElseThrow().broader());
        assertEquals("Activity", graph.findEntity(activity).orElseThrow().label());
        assertEquals(1, graph.findReferencing(activity).size());
    }

    @Test
    void updatesAnEntityWhenItsTagIsRenamed() {
        String skiing = "https://semanticdam.com/id/tag/wknd/activity/skiing";
        graph.writeEntity(new com.semanticdam.core.core.domain.Entity(skiing, Vocabulary.CONCEPT_CLASS, "Old title"));
        dam.put(ASSET, Map.of("cq:tags", new String[] {"wknd:activity/skiing"}));

        pipeline.ingest(ASSET);

        assertEquals("Skiing", graph.findEntity(skiing).orElseThrow().label());
    }

    @Test
    void unknownBrandsAreCandidatesUntilApproved() {
        dam.put(ASSET, Map.of("aem:brand", "Globex", "dc:title", "Hero"));

        AssetMetadata stored = pipeline.ingest(ASSET).orElseThrow();

        assertFalse(value(stored, Vocabulary.BRAND).isValid());
        assertEquals(com.semanticdam.core.core.domain.Entity.Status.CANDIDATE,
                graph.findEntity("https://semanticdam.com/id/brand/globex").orElseThrow().status());

        graph.writeEntity(graph.findEntity("https://semanticdam.com/id/brand/globex").orElseThrow()
                .withStatus(com.semanticdam.core.core.domain.Entity.Status.APPROVED, null));
        assertTrue(value(pipeline.ingest(ASSET).orElseThrow(), Vocabulary.BRAND).isValid());
    }

    @Test
    void rulesMatchGovernedEntitiesThroughTheirLabels() {
        // "Summer Promo 2026" is an alternative label of the campaign the rule names
        dam.put(ASSET, Map.of("workfront:campaignName", "Summer Promo 2026"));

        AssetMetadata stored = pipeline.ingest(ASSET).orElseThrow();

        assertEquals("https://semanticdam.com/id/campaign/summer-promo-2026", value(stored, Vocabulary.PART_OF_CAMPAIGN).objectValue());
        assertEquals("Tier-1-Global", value(stored, PRIORITY).objectValue());
    }

    private static MetadataStatement value(AssetMetadata metadata, String predicate) {
        return metadata.statements().stream()
                .filter(statement -> statement.predicateUri().equals(predicate)).findFirst().orElseThrow();
    }
}
