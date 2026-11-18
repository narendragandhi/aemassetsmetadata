package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChange.ChangeType;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobConsumer.JobResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetMetadataResourceChangeListenerTest {
    private static List<Map<String, Object>> queuedBy(ResourceChange... changes) {
        List<Map<String, Object>> jobs = new ArrayList<>();
        JobManager jobManager = mock(JobManager.class);
        when(jobManager.addJob(eq(AssetIngestJobs.TOPIC), anyMap())).thenAnswer(invocation -> {
            jobs.add(invocation.getArgument(1));
            return mock(Job.class);
        });
        new AssetMetadataResourceChangeListener(jobManager).onChange(List.of(changes));
        return jobs;
    }

    private static ResourceChange change(ChangeType type, String path) {
        return new ResourceChange(type, path, false);
    }

    @Test
    void queuesOneIngestPerAssetForMetadataChanges() {
        List<Map<String, Object>> jobs = queuedBy(
                change(ChangeType.CHANGED, "/content/dam/site/hero.png/jcr:content/metadata"),
                change(ChangeType.CHANGED, "/content/dam/site/hero.png/jcr:content/metadata"),
                change(ChangeType.ADDED, "/content/dam/site/other.png/jcr:content/metadata"));

        assertEquals(List.of(
                Map.of("path", "/content/dam/site/hero.png", "action", "ingest"),
                Map.of("path", "/content/dam/site/other.png", "action", "ingest")), jobs);
    }

    @Test
    void ignoresRenditionsAndOtherContentNodes() {
        JobManager jobManager = mock(JobManager.class);

        new AssetMetadataResourceChangeListener(jobManager).onChange(List.of(
                change(ChangeType.ADDED, "/content/dam/site/hero.png/jcr:content/renditions/cq5dam.thumbnail.png"),
                change(ChangeType.CHANGED, "/content/dam/site/hero.png/jcr:content"),
                change(ChangeType.REMOVED, "/content/dam/site/hero.png/jcr:content/renditions/original"),
                change(ChangeType.CHANGED, "/content/site/page")));

        verify(jobManager, never()).addJob(any(), anyMap());
    }

    @Test
    void queuesRemovalForDeletedAssetsAndFolders() {
        List<Map<String, Object>> jobs = queuedBy(
                change(ChangeType.REMOVED, "/content/dam/site/hero.png"),
                change(ChangeType.REMOVED, "/content/dam/old-campaign"));

        assertEquals(List.of(
                Map.of("path", "/content/dam/site/hero.png", "action", "remove"),
                Map.of("path", "/content/dam/old-campaign", "action", "remove")), jobs);
    }

    @Test
    void queuesIngestForTheTargetOfAMove() {
        List<Map<String, Object>> jobs = queuedBy(change(ChangeType.ADDED, "/content/dam/site/moved.png"));

        assertEquals(List.of(Map.of("path", "/content/dam/site/moved.png", "action", "ingest")), jobs);
    }

    @Test
    void consumerRunsThePipelineAndAsksForRetryOnFailure() {
        List<String> calls = new ArrayList<>();
        AssetMetadataPipeline pipeline = new AssetMetadataPipeline() {
            @Override public Optional<AssetMetadata> ingest(AssetId assetId) {
                if (assetId.path().endsWith("broken.png")) {
                    throw new IllegalStateException("graph unavailable");
                }
                calls.add("ingest " + assetId.path());
                return Optional.empty();
            }
            @Override public void remove(AssetId assetId) { calls.add("remove " + assetId.path()); }
        };
        AssetIngestJobConsumer consumer = new AssetIngestJobConsumer(pipeline);

        assertEquals(JobResult.OK, consumer.process(job("/content/dam/a.png", "ingest")));
        assertEquals(JobResult.OK, consumer.process(job("/content/dam/b.png", "remove")));
        assertEquals(JobResult.FAILED, consumer.process(job("/content/dam/broken.png", "ingest")));
        assertEquals(JobResult.CANCEL, consumer.process(job("/etc/passwd", "ingest")));
        assertEquals(List.of("ingest /content/dam/a.png", "remove /content/dam/b.png"), calls);
    }

    private static Job job(String path, String action) {
        Job job = mock(Job.class);
        when(job.getProperty("path", String.class)).thenReturn(path);
        when(job.getProperty("action", String.class)).thenReturn(action);
        return job;
    }
}
