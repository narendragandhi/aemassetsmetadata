package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.EmbeddingVector;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.Designate;

import java.time.Duration;
import java.util.List;

/** OSGi wiring for Qdrant; absent configuration keeps the service inactive. */
@Component(service = VectorSearchIndex.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = QdrantVectorIndexConfig.class)
public final class QdrantVectorIndexComponent implements VectorSearchIndex {
    private QdrantVectorSearchIndex delegate;

    @Activate
    void activate(QdrantVectorIndexConfig config) {
        if (config.timeoutSeconds() <= 0) throw new IllegalArgumentException("timeoutSeconds must be positive");
        delegate = new QdrantVectorSearchIndex(config.baseUrl(), config.apiKey(), config.collection(),
                config.modelId(), config.modelVersion(), Duration.ofSeconds(config.timeoutSeconds()));
    }

    @Override
    public void upsert(AssetId assetId, EmbeddingVector embedding) { requireDelegate().upsert(assetId, embedding); }
    @Override
    public void remove(AssetId assetId) { requireDelegate().remove(assetId); }
    @Override
    public List<VectorMatch> search(EmbeddingVector query, int limit) { return requireDelegate().search(query, limit); }

    private QdrantVectorSearchIndex requireDelegate() {
        if (delegate == null) throw new IllegalStateException("Qdrant vector index is not active");
        return delegate;
    }
}
