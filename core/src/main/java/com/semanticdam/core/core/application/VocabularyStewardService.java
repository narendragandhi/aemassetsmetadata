package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import com.semanticdam.core.core.graph.OntologyRegistry;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Governs controlled-vocabulary entries that arrive from sources that are not the authority for
 * them. A steward approves a candidate, rejects it, or merges it into an existing entry (the
 * candidate's label becomes a hidden label of the target, so the same spelling resolves to the
 * target from then on). Every decision re-ingests the assets that use the entry.
 */
@Component(service = {VocabularyStewardService.class, JobConsumer.class},
        property = JobConsumer.PROPERTY_TOPICS + "=" + VocabularyStewardService.REINGEST_TOPIC)
public class VocabularyStewardService implements JobConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(VocabularyStewardService.class);

    public enum Decision { APPROVE, REJECT, MERGE }

    static final String REINGEST_TOPIC = "com/semanticdam/vocabulary/reingest";
    static final String PROPERTY_ENTITY = "entity";
    static final long REINGEST_DELAY_MS = 15_000;

    /** The outcome of a decision: the updated entry and how many assets were queued for re-ingest. */
    public record Result(Entity entity, int assetsQueued) {
    }

    private final KnowledgeGraphService graph;
    private final OntologyRegistry ontology;
    private final JobManager jobManager;

    @Activate
    public VocabularyStewardService(@Reference KnowledgeGraphService graph, @Reference OntologyRegistry ontology,
            @Reference JobManager jobManager) {
        this.graph = graph;
        this.ontology = ontology;
        this.jobManager = jobManager;
    }

    /** Queues ingest for every asset currently found linking to the entry. */
    int reingestUsers(String entityUri) {
        Set<String> paths = new LinkedHashSet<>();
        for (AssetMetadata asset : graph.findReferencing(entityUri)) {
            paths.add(asset.assetId().path());
        }
        for (String path : paths) {
            jobManager.addJob(AssetIngestJobs.TOPIC, Map.of(
                    AssetIngestJobs.PROPERTY_PATH, path, AssetIngestJobs.PROPERTY_ACTION, AssetIngestJobs.ACTION_INGEST));
        }
        return paths.size();
    }

    public Collection<Entity> candidates(String typeUri) {
        return graph.findEntities(Entity.Status.CANDIDATE, typeUri);
    }

    /** Assets that link to an entry, for showing the steward what a decision affects. */
    public Collection<AssetMetadata> usage(String entityUri) {
        return graph.findReferencing(entityUri);
    }

    /** The delayed second pass after a decision. */
    @Override
    public JobResult process(Job job) {
        String entity = job.getProperty(PROPERTY_ENTITY, String.class);
        if (entity == null) {
            return JobResult.CANCEL;
        }
        try {
            LOG.debug("Second-pass re-ingest for {}: {} asset(s)", entity, reingestUsers(entity));
            return JobResult.OK;
        } catch (RuntimeException e) {
            return JobResult.FAILED;
        }
    }

    public Result decide(String entityUri, Decision decision, String targetUri, String actor) {
        Entity entity = graph.findEntity(entityUri)
                .orElseThrow(() -> new IllegalArgumentException("unknown vocabulary entry " + entityUri));
        if (entity.status() == Entity.Status.MERGED) {
            throw new IllegalStateException("entry was already merged into " + entity.replacedBy());
        }
        Entity updated;
        switch (decision) {
            case APPROVE -> updated = entity.withStatus(Entity.Status.APPROVED, null);
            case REJECT -> updated = entity.withStatus(Entity.Status.REJECTED, null);
            case MERGE -> {
                if (targetUri == null || targetUri.equals(entityUri)) {
                    throw new IllegalArgumentException("merge needs a different target entry");
                }
                Optional<Entity> declared = ontology.findEntity(targetUri);
                Entity target = declared.or(() -> graph.findEntity(targetUri))
                        .orElseThrow(() -> new IllegalArgumentException("unknown target entry " + targetUri));
                if (!target.typeUri().equals(entity.typeUri())) {
                    throw new IllegalArgumentException("target entry is of a different class");
                }
                if (!target.approved()) {
                    throw new IllegalStateException("target entry must be approved");
                }
                if (declared.isEmpty()) {
                    graph.writeEntity(target.withHiddenLabel(entity.label()));
                }
                updated = entity.withStatus(Entity.Status.MERGED, target.uri());
            }
            default -> throw new IllegalArgumentException("unknown decision");
        }
        graph.writeEntity(updated);
        int queued = reingestUsers(entityUri);
        // The statement index is updated asynchronously: assets ingested moments ago may not be
        // found yet. A second pass shortly after catches them; re-ingest is idempotent.
        jobManager.createJob(REINGEST_TOPIC).properties(Map.of(PROPERTY_ENTITY, entityUri))
                .schedule().at(new java.util.Date(System.currentTimeMillis() + REINGEST_DELAY_MS)).add();
        LOG.info("Vocabulary decision by {}: {} {} -> {} ({} assets re-ingested now, second pass scheduled)",
                actor, decision, entityUri, updated.status(), queued);
        return new Result(updated, queued);
    }
}
