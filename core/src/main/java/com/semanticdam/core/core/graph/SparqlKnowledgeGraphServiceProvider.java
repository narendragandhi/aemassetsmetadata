package com.semanticdam.core.core.graph;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

import java.time.Duration;

@Component(service = SparqlKnowledgeGraphServiceProvider.class,
        configurationPid = "com.semanticdam.core.core.graph.SparqlEndpointConfig")
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
        HttpSparqlClient client = new HttpSparqlClient(config.endpointUrl(), config.updateUrl(),
                config.username(), config.password(), Duration.ofSeconds(Math.max(1, config.timeoutSeconds())));
        this.store = new SparqlKnowledgeGraphStore(client, new GraphSubjectResolver(), config.graphUri());
    }

    public SparqlKnowledgeGraphStore store() {
        return store;
    }
}
