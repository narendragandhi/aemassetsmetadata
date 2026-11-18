package com.semanticdam.core.core.application;

import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChange.ChangeType;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns DAM changes into ingest jobs. The listener does no repository or graph work itself: the
 * observation thread only decides which asset a change belongs to and queues a job.
 *
 * <ul>
 *   <li>a change to {@code <asset>/jcr:content/metadata} re-ingests that asset;</li>
 *   <li>a node added outside {@code jcr:content} (an upload, or the target of a move) is ingested
 *       if it turns out to be an asset;</li>
 *   <li>a node removed outside {@code jcr:content} removes that asset or folder from the graph.</li>
 * </ul>
 * Renditions, sub-assets and other {@code jcr:content} children are ignored. Assets inside a moved
 * folder are not reported individually by the repository; use the reindex endpoint for those.
 */
@Component(
        service = ResourceChangeListener.class,
        property = {
                ResourceChangeListener.PATHS + "=/content/dam",
                ResourceChangeListener.CHANGES + "=ADDED",
                ResourceChangeListener.CHANGES + "=CHANGED",
                ResourceChangeListener.CHANGES + "=REMOVED"
        }
)
public class AssetMetadataResourceChangeListener implements ResourceChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetMetadataResourceChangeListener.class);
    private static final String METADATA_SUFFIX = "/jcr:content/metadata";
    private static final String JCR_CONTENT = "/jcr:content";

    private final JobManager jobManager;

    @Activate
    public AssetMetadataResourceChangeListener(@Reference JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @Override
    public void onChange(List<ResourceChange> changes) {
        // One batch often reports the same asset several times; queue each asset once.
        Map<String, String> actions = new LinkedHashMap<>();
        for (ResourceChange change : changes) {
            String path = change.getPath();
            if (path == null || !path.startsWith("/content/dam/")) {
                continue;
            }
            boolean insideContent = path.contains(JCR_CONTENT);
            if (path.endsWith(METADATA_SUFFIX)) {
                if (change.getType() != ChangeType.REMOVED) {
                    actions.putIfAbsent(path.substring(0, path.length() - METADATA_SUFFIX.length()),
                            AssetIngestJobs.ACTION_INGEST);
                }
            } else if (!insideContent && change.getType() == ChangeType.REMOVED) {
                actions.put(path, AssetIngestJobs.ACTION_REMOVE);
            } else if (!insideContent && change.getType() == ChangeType.ADDED) {
                actions.putIfAbsent(path, AssetIngestJobs.ACTION_INGEST);
            }
        }
        actions.forEach((path, action) -> {
            if (jobManager.addJob(AssetIngestJobs.TOPIC, Map.of(
                    AssetIngestJobs.PROPERTY_PATH, path, AssetIngestJobs.PROPERTY_ACTION, action)) == null) {
                LOGGER.error("Could not queue {} job for {}", action, path);
            }
        });
    }
}
