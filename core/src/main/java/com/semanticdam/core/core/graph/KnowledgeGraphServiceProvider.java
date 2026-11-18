package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;

@Component(service = KnowledgeGraphService.class,
        configurationPid = "com.semanticdam.core.core.graph.GraphStorageConfig")
@Designate(ocd = GraphStorageConfig.class)
public class KnowledgeGraphServiceProvider implements KnowledgeGraphService {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeGraphServiceProvider.class);
    private final InMemoryKnowledgeGraphStore inMemory = new InMemoryKnowledgeGraphStore();
    private volatile KnowledgeGraphService delegate;
    private volatile SparqlKnowledgeGraphServiceProvider sparqlProvider;
    private volatile String currentMode = "in-memory";

    @Reference
    private org.apache.sling.api.resource.ResourceResolverFactory resolverFactory;

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
            LOG.warn("Graph storage mode 'sparql' is configured but no endpoint is set; using in-memory storage");
        } else if ("jcr".equalsIgnoreCase(mode)) {
            return new JcrKnowledgeGraphStore(resolverFactory);
        } else if (!"in-memory".equalsIgnoreCase(mode)) {
            LOG.warn("Graph storage mode '{}' is unknown or not available; using in-memory storage", mode);
        }
        // Keep one instance so that a rebind does not silently discard transient data.
        return inMemory;
    }

    @Override
    public void writeAssetMetadata(AssetMetadata metadata) {
        delegate.writeAssetMetadata(metadata);
    }

    @Override
    public void deleteAsset(AssetId assetId) {
        delegate.deleteAsset(assetId);
    }

    @Override
    public Optional<AssetMetadata> findByAssetId(AssetId assetId) {
        return delegate.findByAssetId(assetId);
    }

    @Override
    public Collection<AssetMetadata> findByPredicateValue(String predicateUri, String objectValue) {
        return delegate.findByPredicateValue(predicateUri, objectValue);
    }

    @Override
    public Collection<AssetMetadata> findReferencing(String entityUri) {
        return delegate.findReferencing(entityUri);
    }

    @Override
    public Collection<AssetMetadata> findRelated(AssetId assetId) {
        return delegate.findRelated(assetId);
    }

    @Override
    public void writeEntity(Entity entity) {
        delegate.writeEntity(entity);
    }

    @Override
    public Optional<Entity> findEntity(String entityUri) {
        return delegate.findEntity(entityUri);
    }

    @Override
    public Collection<Entity> findEntities(Entity.Status status, String typeUri) {
        return delegate.findEntities(status, typeUri);
    }

    /** The storage mode actually in use, which differs from the configured one if it is unavailable. */
    public String activeMode() {
        return delegate instanceof JcrKnowledgeGraphStore ? "jcr"
                : delegate instanceof SparqlKnowledgeGraphStore ? "sparql" : "in-memory";
    }
}
