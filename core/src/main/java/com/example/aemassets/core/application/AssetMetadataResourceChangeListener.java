package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.api.resource.observation.ResourceChangeListener.ChangeType;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Component(
        service = ResourceChangeListener.class,
        property = {
                ResourceChangeListener.PATHS + "=/content/dam",
                ResourceChangeListener.CHANGES + "=ADDED",
                ResourceChangeListener.CHANGES + "=CHANGED"
        }
)
public class AssetMetadataResourceChangeListener implements ResourceChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetMetadataResourceChangeListener.class);
    private static final String METADATA_SUFFIX = "/jcr:content/metadata";
    private static final String JCR_CONTENT_SUFFIX = "/jcr:content";

    private final AssetMetadataPipeline pipeline;

    public AssetMetadataResourceChangeListener(@Reference AssetMetadataPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public void onChange(List<ResourceChange> changes) {
        for (ResourceChange change : changes) {
            if (change.getType() != ChangeType.ADDED && change.getType() != ChangeType.CHANGED) {
                continue;
            }
            String assetPath = normalizeAssetPath(change.getPath());
            if (assetPath == null) {
                continue;
            }
            try {
                pipeline.ingest(AssetId.of(assetPath));
            } catch (RuntimeException ex) {
                LOGGER.warn("Failed to ingest asset metadata for {}", assetPath, ex);
            }
        }
    }

    private String normalizeAssetPath(String path) {
        if (path == null || !path.startsWith("/content/dam")) {
            return null;
        }
        if (path.endsWith(METADATA_SUFFIX)) {
            return path.substring(0, path.length() - METADATA_SUFFIX.length());
        }
        if (path.endsWith(JCR_CONTENT_SUFFIX)) {
            return path.substring(0, path.length() - JCR_CONTENT_SUFFIX.length());
        }
        return path;
    }
}
