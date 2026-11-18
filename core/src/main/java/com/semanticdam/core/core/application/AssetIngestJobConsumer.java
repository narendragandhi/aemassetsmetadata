package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the pipeline for one asset. A failure returns {@link JobResult#FAILED} so that Sling
 * retries the job; nothing is dropped because the repository or the graph was briefly unavailable.
 */
@Component(service = JobConsumer.class, property = JobConsumer.PROPERTY_TOPICS + "=" + AssetIngestJobs.TOPIC)
public class AssetIngestJobConsumer implements JobConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(AssetIngestJobConsumer.class);

    private final AssetMetadataPipeline pipeline;
    private final JobManager jobManager;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    private volatile SemanticSearchService semanticSearch;

    @Activate
    public AssetIngestJobConsumer(@Reference AssetMetadataPipeline pipeline, @Reference JobManager jobManager) {
        this.pipeline = pipeline;
        this.jobManager = jobManager;
    }

    public AssetIngestJobConsumer(AssetMetadataPipeline pipeline) {
        this(pipeline, null);
    }

    void setSemanticSearch(SemanticSearchService semanticSearch) {
        this.semanticSearch = semanticSearch;
    }

    @Override
    public JobResult process(Job job) {
        String path = job.getProperty(AssetIngestJobs.PROPERTY_PATH, String.class);
        if (path == null || !path.startsWith("/content/dam/")) {
            LOG.warn("Discarding ingest job without a DAM path: {}", path);
            return JobResult.CANCEL;
        }
        try {
            String action = AssetIngestJobs.ACTION_REMOVE.equals(
                    job.getProperty(AssetIngestJobs.PROPERTY_ACTION, String.class))
                    ? AssetIngestJobs.ACTION_REMOVE : AssetIngestJobs.ACTION_INGEST;
            if (AssetIngestJobs.ACTION_REMOVE.equals(action)) {
                pipeline.remove(AssetId.of(path));
            } else if (pipeline.ingest(AssetId.of(path)).isEmpty()) {
                action = AssetIngestJobs.ACTION_REMOVE;
            }
            if (semanticSearch != null && jobManager != null) {
                jobManager.addJob(AssetIngestJobs.EMBED_TOPIC, Map.of(
                        AssetIngestJobs.PROPERTY_PATH, path, AssetIngestJobs.PROPERTY_ACTION, action));
            }
            return JobResult.OK;
        } catch (RuntimeException ex) {
            LOG.warn("Ingest of {} failed (attempt {}); it will be retried", path, job.getRetryCount() + 1, ex);
            return JobResult.FAILED;
        }
    }
}
