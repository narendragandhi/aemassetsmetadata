package com.example.aemassets.core.graph;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

@Component(service = SparqlKnowledgeGraphServiceProvider.class)
@Designate(ocd = SparqlEndpointConfig.class)
public class SparqlKnowledgeGraphServiceProvider {
    private volatile SparqlKnowledgeGraphStore store;

    @Activate
    @Modified
    protected void activate(SparqlEndpointConfig config) {
        if (config.endpointUrl() == null || config.endpointUrl().isBlank()) {
            this.store = null;
            return;
        }
        HttpSparqlClient client = new HttpSparqlClient(
                config.endpointUrl(),
                config.username(),
                config.password()
        );
        this.store = new SparqlKnowledgeGraphStore(
                client,
                new AssetMetadataTripleMapper(),
                new SimpleTurtleSerializer(),
                new SparqlQueryBuilder(),
                new NoopSparqlResultParser(),
                config.graphUri()
        );
    }

    public SparqlKnowledgeGraphStore store() {
        return store;
    }
}
