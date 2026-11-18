package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.update.UpdateAction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the store against Apache Jena's in-memory SPARQL engine, so queries and updates are real SPARQL. */
class SparqlKnowledgeGraphStoreTest {
    private static final AssetId HERO = AssetId.of("/content/dam/site/hero \"1\".png");
    private static final AssetId BANNER = AssetId.of("/content/dam/site/banner.png");
    private static final String ACME = "https://semanticdam.com/id/brand/acme";

    /** A SPARQL endpoint backed by an in-memory Jena dataset. */
    static final class JenaClient implements SparqlClient {
        final Dataset dataset = DatasetFactory.createTxnMem();
        int requests;

        @Override
        public void update(String sparqlUpdate) {
            requests++;
            UpdateAction.parseExecute(sparqlUpdate, dataset);
        }

        @Override
        public Optional<String> query(String sparqlQuery) {
            requests++;
            try (QueryExecution execution = QueryExecutionFactory.create(sparqlQuery, dataset)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ResultSetFormatter.outputAsJSON(out, execution.execSelect());
                return Optional.of(out.toString(StandardCharsets.UTF_8));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://semanticdam.com/graph/assets"})
    void roundTripsStatementsWithProvenanceAndOrder(String graphUri) {
        SparqlKnowledgeGraphStore store = new SparqlKnowledgeGraphStore(new JenaClient(), new GraphSubjectResolver(), graphUri);
        AssetMetadata metadata = AssetMetadata.empty(HERO)
                .add(MetadataStatement.literal(Vocabulary.TITLE, "Hero \"quoted\"\nline two", "AEM", true, 0.75))
                .add(MetadataStatement.uri(Vocabulary.BRAND, ACME, "AEM", true, 0.75))
                .add(MetadataStatement.literal(Vocabulary.SKU, "bad sku", "PIM", false, 0.95))
                // the same fact from two sources must survive as two statements
                .add(MetadataStatement.uri(Vocabulary.BRAND, ACME, "AI:model", true, 0.9));

        store.writeAssetMetadata(metadata);

        assertEquals(metadata, store.findByAssetId(HERO).orElseThrow());
    }

    @Test
    void rewritingReplacesInsteadOfAppending() {
        SparqlKnowledgeGraphStore store = new SparqlKnowledgeGraphStore(new JenaClient(), new GraphSubjectResolver(), null);
        store.writeAssetMetadata(AssetMetadata.empty(HERO).add(MetadataStatement.literal(Vocabulary.TITLE, "One", "AEM")));
        AssetMetadata second = AssetMetadata.empty(HERO).add(MetadataStatement.literal(Vocabulary.TITLE, "Two", "AEM"));

        store.writeAssetMetadata(second);

        assertEquals(second, store.findByAssetId(HERO).orElseThrow());
        store.writeAssetMetadata(AssetMetadata.empty(HERO));
        assertTrue(store.findByAssetId(HERO).isEmpty());
    }

    @Test
    void findsAssetsByFactAndByEntityAndDeletes() {
        JenaClient client = new JenaClient();
        SparqlKnowledgeGraphStore store = new SparqlKnowledgeGraphStore(client, new GraphSubjectResolver(), null);
        store.writeAssetMetadata(AssetMetadata.empty(HERO)
                .add(MetadataStatement.uri(Vocabulary.BRAND, ACME, "AEM"))
                .add(MetadataStatement.uri(Vocabulary.RDF_TYPE, Vocabulary.ASSET, "AEM"))
                .add(MetadataStatement.literal(Vocabulary.SKU, "SUM-1", "PIM")));
        store.writeAssetMetadata(AssetMetadata.empty(BANNER)
                .add(MetadataStatement.uri(Vocabulary.BRAND, ACME, "AEM"))
                .add(MetadataStatement.uri(Vocabulary.RDF_TYPE, Vocabulary.ASSET, "AEM")));

        assertEquals(List.of(HERO), ids(store.findByPredicateValue(Vocabulary.SKU, "SUM-1")));
        assertEquals(List.of(BANNER, HERO), ids(store.findReferencing(ACME)));
        assertTrue(store.findReferencing(Vocabulary.ASSET).isEmpty());
        assertEquals(List.of(BANNER), ids(store.findRelated(HERO)));

        store.deleteAsset(HERO);
        assertTrue(store.findByAssetId(HERO).isEmpty());
        assertEquals(List.of(BANNER), ids(store.findReferencing(ACME)));
        // nothing about the deleted asset is left behind
        assertEquals(0, client.dataset.getDefaultModel().listStatements().toList().stream()
                .filter(statement -> statement.getObject().toString().contains("hero")
                        || statement.getSubject().toString().contains("hero")).count());
    }

    @Test
    void storesAndRelabelsEntities() {
        SparqlKnowledgeGraphStore store = new SparqlKnowledgeGraphStore(new JenaClient(), new GraphSubjectResolver(), null);
        store.writeEntity(new Entity(ACME, Vocabulary.BRAND_CLASS, "Acme"));
        store.writeEntity(new Entity(ACME, Vocabulary.BRAND_CLASS, "ACME Corp"));
        Entity tag = new Entity("https://semanticdam.com/id/tag/wknd/a/b", Vocabulary.CONCEPT_CLASS, "B",
                List.of("https://semanticdam.com/id/tag/wknd/a"));
        store.writeEntity(tag);
        store.writeEntity(tag);

        assertEquals(new Entity(ACME, Vocabulary.BRAND_CLASS, "ACME Corp"), store.findEntity(ACME).orElseThrow());
        assertEquals(tag, store.findEntity(tag.uri()).orElseThrow());
        assertTrue(store.findEntity("https://semanticdam.com/id/brand/none").isEmpty());
    }

    @Test
    void reportsEndpointFailuresInsteadOfReturningNothing() {
        SparqlClient broken = new SparqlClient() {
            @Override public void update(String sparqlUpdate) { throw new IllegalStateException("HTTP 503"); }
            @Override public Optional<String> query(String sparqlQuery) { return Optional.of("<html>error</html>"); }
        };
        SparqlKnowledgeGraphStore store = new SparqlKnowledgeGraphStore(broken, new GraphSubjectResolver(), null);

        assertThrows(GraphStoreException.class, () -> store.findByAssetId(HERO));
        assertThrows(GraphStoreException.class, () -> store.writeAssetMetadata(AssetMetadata.empty(HERO)));
        assertThrows(GraphStoreException.class, () -> store.findReferencing(ACME));
    }

    private static List<AssetId> ids(java.util.Collection<AssetMetadata> assets) {
        return assets.stream().map(AssetMetadata::assetId).toList();
    }
}
