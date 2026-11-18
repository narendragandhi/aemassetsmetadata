package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.domain.OntologyTerm;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import com.semanticdam.core.core.graph.OntologyRegistry;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Semantic search over the governed facts of assets. Each asset is embedded as a short text built
 * from its valid facts, with entity labels instead of IRIs ("Brand: Summit. Subject: Organic
 * Cotton, Sustainable Product."), so the vector reflects the same knowledge the graph holds.
 *
 * <p>The component exists only while both an {@link EmbeddingProvider} and a
 * {@link VectorSearchIndex} are configured. It also consumes the embed jobs that the ingest job
 * queues after a successful ingest, so an embedding outage is retried without re-running ingest.</p>
 */
@Component(service = {SemanticSearchService.class, JobConsumer.class},
        property = JobConsumer.PROPERTY_TOPICS + "=" + AssetIngestJobs.EMBED_TOPIC)
public class SemanticSearchService implements JobConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(SemanticSearchService.class);
    private static final int MAX_TEXT_LENGTH = 4000;

    private final EmbeddingProvider embeddings;
    private final VectorSearchIndex index;
    private final KnowledgeGraphService graph;
    private final OntologyRegistry ontology;

    @Activate
    public SemanticSearchService(@Reference EmbeddingProvider embeddings, @Reference VectorSearchIndex index,
            @Reference KnowledgeGraphService graph, @Reference OntologyRegistry ontology) {
        this.embeddings = embeddings;
        this.index = index;
        this.graph = graph;
        this.ontology = ontology;
    }

    /** Embeds the asset's current facts, or removes it from the index if it has none. */
    public void index(AssetId assetId) {
        Optional<String> text = graph.findByAssetId(assetId).map(this::text).filter(value -> !value.isBlank());
        if (text.isEmpty()) {
            index.remove(assetId);
            return;
        }
        index.upsert(assetId, embeddings.embed(text.get()));
    }

    public void remove(AssetId assetId) {
        index.remove(assetId);
    }

    /** Nearest assets to a natural-language query, best first. Callers filter by access. */
    public List<VectorMatch> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must be provided");
        }
        return index.search(embeddings.embed(query), limit);
    }

    /** Vector recall with a deterministic boost for assets that also match a graph fact. */
    public List<HybridRetrievalResult> hybrid(String query, String predicateUri, String objectValue, int limit) {
        return new HybridAssetRetriever(embeddings, index, graph).retrieve(query, predicateUri, objectValue, limit);
    }

    @Override
    public JobResult process(Job job) {
        String path = job.getProperty(AssetIngestJobs.PROPERTY_PATH, String.class);
        if (path == null || !path.startsWith("/content/dam/")) {
            return JobResult.CANCEL;
        }
        try {
            if (AssetIngestJobs.ACTION_REMOVE.equals(job.getProperty(AssetIngestJobs.PROPERTY_ACTION, String.class))) {
                remove(AssetId.of(path));
            } else {
                index(AssetId.of(path));
            }
            return JobResult.OK;
        } catch (RuntimeException e) {
            LOG.warn("Embedding of {} failed (attempt {}); it will be retried", path, job.getRetryCount() + 1, e);
            return JobResult.FAILED;
        }
    }

    /** The text that represents an asset in the vector index. */
    String text(AssetMetadata metadata) {
        Map<String, Set<String>> byLabel = new LinkedHashMap<>();
        for (MetadataStatement statement : metadata.statements()) {
            if (!statement.isValid() || Vocabulary.RDF_TYPE.equals(statement.predicateUri())) {
                continue;
            }
            String label = ontology.findByUri(statement.predicateUri()).map(OntologyTerm::label)
                    .orElse(statement.predicateUri());
            String value = statement.objectIsUri()
                    ? ontology.findEntity(statement.objectValue()).or(() -> graph.findEntity(statement.objectValue()))
                            .map(Entity::label).orElse(null)
                    : statement.objectValue();
            if (value != null && !value.isBlank()) {
                byLabel.computeIfAbsent(label, key -> new LinkedHashSet<>()).add(value);
            }
        }
        StringBuilder text = new StringBuilder();
        byLabel.forEach((label, values) -> text.append(label).append(": ").append(String.join(", ", values)).append(".\n"));
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text.toString().trim();
    }
}
