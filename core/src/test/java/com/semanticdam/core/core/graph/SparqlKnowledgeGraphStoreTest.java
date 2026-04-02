package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SparqlKnowledgeGraphStoreTest {
    @Test
    void buildsInsertUpdate() {
        RecordingSparqlClient client = new RecordingSparqlClient();
        SparqlKnowledgeGraphStore store = new SparqlKnowledgeGraphStore(
                client,
                new AssetMetadataTripleMapper(),
                new SimpleTurtleSerializer(),
                new SparqlQueryBuilder(),
                new NoopSparqlResultParser(),
                "https://example.com/graph"
        );

        AssetMetadata metadata = AssetMetadata.empty(AssetId.of("/content/dam/site/hero.png"))
                .add(MetadataStatement.literal("http://purl.org/dc/terms/title", "Hero"));

        store.writeAssetMetadata(metadata);

        assertTrue(client.updates.get(0).contains("GRAPH <https://example.com/graph>"));
        assertTrue(client.updates.get(0).contains("INSERT DATA"));
    }

    @Test
    void buildsSelectQuery() {
        RecordingSparqlClient client = new RecordingSparqlClient();
        SparqlKnowledgeGraphStore store = new SparqlKnowledgeGraphStore(
                client,
                new AssetMetadataTripleMapper(),
                new SimpleTurtleSerializer(),
                new SparqlQueryBuilder(),
                new NoopSparqlResultParser(),
                "https://example.com/graph"
        );

        store.findByAssetId(AssetId.of("/content/dam/site/hero.png"));

        assertTrue(client.queries.get(0).contains("SELECT"));
        assertTrue(client.queries.get(0).contains("GRAPH <https://example.com/graph>"));
    }

    private static class RecordingSparqlClient implements SparqlClient {
        private final List<String> updates = new ArrayList<>();
        private final List<String> queries = new ArrayList<>();

        @Override
        public void update(String sparqlUpdate) {
            updates.add(sparqlUpdate);
        }

        @Override
        public Optional<String> query(String sparqlQuery) {
            queries.add(sparqlQuery);
            return Optional.empty();
        }
    }
}
