package com.example.aemassets.core.graph;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;

@Component(service = KnowledgeGraphService.class)
@Designate(ocd = GraphStorageConfig.class)
public class KnowledgeGraphServiceProvider implements KnowledgeGraphService {
    private volatile KnowledgeGraphService delegate;
    private volatile SparqlKnowledgeGraphServiceProvider sparqlProvider;
    private volatile String currentMode = "in-memory";

    @Activate
    @Modified
    protected void activate(GraphStorageConfig config) {
        this.currentMode = config.mode();
        this.delegate = resolveDelegate(currentMode);
    }

    @Reference(
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC
    )
    protected void bindSparqlProvider(SparqlKnowledgeGraphServiceProvider provider) {
        this.sparqlProvider = provider;
        this.delegate = resolveDelegate(currentMode);
    }

    protected void unbindSparqlProvider(SparqlKnowledgeGraphServiceProvider provider) {
        if (this.sparqlProvider == provider) {
            this.sparqlProvider = null;
            this.delegate = resolveDelegate(currentMode);
        }
    }

    private KnowledgeGraphService resolveDelegate(String mode) {
        if ("sparql".equalsIgnoreCase(mode) && sparqlProvider != null) {
            SparqlKnowledgeGraphStore store = sparqlProvider.store();
            if (store != null) {
                return store;
            }
        }
        return new InMemoryKnowledgeGraphStore();
    }

    @Override
    public void writeAssetMetadata(com.example.aemassets.core.domain.AssetMetadata metadata) {
        delegate.writeAssetMetadata(metadata);
    }

    @Override
    public java.util.Optional<com.example.aemassets.core.domain.AssetMetadata> findByAssetId(
            com.example.aemassets.core.domain.AssetId assetId) {
        return delegate.findByAssetId(assetId);
    }
}
