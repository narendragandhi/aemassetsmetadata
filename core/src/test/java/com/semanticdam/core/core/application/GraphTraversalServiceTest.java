package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.InMemoryKnowledgeGraphStore;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphTraversalServiceTest {
    private static final String ACME = "https://semanticdam.com/id/brand/acme";
    private static final String SUMMER = "https://semanticdam.com/id/campaign/summer";
    private static final String WINTER = "https://semanticdam.com/id/campaign/winter";
    private static final String ORPHAN_BRAND = "https://semanticdam.com/id/brand/other";

    private final InMemoryKnowledgeGraphStore graph = new InMemoryKnowledgeGraphStore();
    private final GraphTraversalService traversal = new GraphTraversalService(graph);

    @BeforeEach
    void setUp() {
        // a --acme,summer-- b ; b --winter-- c ; d is isolated ; a --acme-- e
        asset("/content/dam/a.jpg", brand(ACME), campaign(SUMMER));
        asset("/content/dam/b.jpg", brand(ACME), campaign(SUMMER), campaign(WINTER));
        asset("/content/dam/c.jpg", campaign(WINTER));
        asset("/content/dam/d.jpg", brand(ORPHAN_BRAND));
        asset("/content/dam/e.jpg", brand(ACME));
    }

    @Test
    void ranksRelatedAssetsBySharedEntitiesAndExplainsWhy() {
        List<GraphTraversalService.RelatedAsset> related = traversal.related(AssetId.of("/content/dam/a.jpg"), 10);

        assertEquals(List.of("/content/dam/b.jpg", "/content/dam/e.jpg"),
                related.stream().map(item -> item.asset().assetId().path()).toList());
        assertEquals(List.of(new GraphTraversalService.Link(Vocabulary.BRAND, ACME),
                new GraphTraversalService.Link(Vocabulary.PART_OF_CAMPAIGN, SUMMER)), related.get(0).sharedLinks());
        assertEquals(1, traversal.related(AssetId.of("/content/dam/a.jpg"), 1).size());
        assertTrue(related.get(0).score() > related.get(1).score());
        assertTrue(traversal.related(AssetId.of("/content/dam/missing.jpg"), 10).isEmpty());
    }

    @Test
    void findsMultiHopPathsBetweenAssets() {
        List<GraphTraversalService.PathStep> path = traversal.shortestPath(
                AssetId.of("/content/dam/a.jpg"), "/content/dam/c.jpg", 6).orElseThrow();

        assertEquals(5, path.size());
        assertEquals("/content/dam/a.jpg", path.get(0).node());
        assertEquals("/content/dam/b.jpg", path.get(2).node());
        assertEquals(WINTER, path.get(3).node());
        assertEquals(Vocabulary.PART_OF_CAMPAIGN, path.get(3).predicateUri());
        assertEquals("/content/dam/c.jpg", path.get(4).node());
        assertTrue(path.get(4).asset());
    }

    @Test
    void findsPathsToEntitiesAndRespectsTheHopLimit() {
        assertEquals(2, traversal.shortestPath(AssetId.of("/content/dam/a.jpg"), ACME, 6).orElseThrow().size());
        assertTrue(traversal.shortestPath(AssetId.of("/content/dam/a.jpg"), "/content/dam/c.jpg", 3).isEmpty());
        assertTrue(traversal.shortestPath(AssetId.of("/content/dam/a.jpg"), "/content/dam/d.jpg", 10).isEmpty());
    }

    /** Sharing one rare entity outranks sharing several that nearly every asset has. */
    @Test
    void weightsSharedEntitiesByRarity() {
        String region = "https://semanticdam.com/id/tag/region";
        String season = "https://semanticdam.com/id/tag/season";
        String product = "https://semanticdam.com/id/product/p1";
        MetadataStatement[] common = {MetadataStatement.uri(Vocabulary.SUBJECT, region, "Inference-Engine"),
                MetadataStatement.uri(Vocabulary.SUBJECT, season, "Inference-Engine")};
        for (int i = 0; i < 20; i++) {
            asset("/content/dam/common/" + i + ".jpg", common);
        }
        asset("/content/dam/x.jpg", common[0], common[1], MetadataStatement.uri(Vocabulary.DEPICTS_PRODUCT, product, "PIM"));
        asset("/content/dam/y.jpg", MetadataStatement.uri(Vocabulary.DEPICTS_PRODUCT, product, "PIM"));

        assertEquals("/content/dam/y.jpg",
                traversal.related(AssetId.of("/content/dam/x.jpg"), 5).get(0).asset().assetId().path());
    }

    @Test
    void ignoresInvalidLinks() {
        asset("/content/dam/f.jpg", MetadataStatement.uri(Vocabulary.BRAND, ORPHAN_BRAND, "AEM", false));

        assertTrue(traversal.related(AssetId.of("/content/dam/f.jpg"), 10).isEmpty());
    }

    private void asset(String path, MetadataStatement... statements) {
        graph.writeAssetMetadata(new AssetMetadata(AssetId.of(path), List.of(statements)));
    }

    private static MetadataStatement brand(String uri) {
        return MetadataStatement.uri(Vocabulary.BRAND, uri, "AEM");
    }

    private static MetadataStatement campaign(String uri) {
        return MetadataStatement.uri(Vocabulary.PART_OF_CAMPAIGN, uri, "Workfront");
    }
}
