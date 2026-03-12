package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetMetadataResourceChangeListenerTest {
    @Test
    void normalizesMetadataPaths() {
        RecordingPipeline pipeline = new RecordingPipeline();
        AssetMetadataResourceChangeListener listener = new AssetMetadataResourceChangeListener(pipeline);

        ResourceChange change = new ResourceChange(ResourceChange.ChangeType.CHANGED,
                "/content/dam/site/hero.png/jcr:content/metadata", false);

        listener.onChange(List.of(change));

        assertEquals("/content/dam/site/hero.png", pipeline.lastPath);
    }

    private static class RecordingPipeline implements AssetMetadataPipeline {
        private String lastPath;

        @Override
        public AssetMetadata ingest(AssetId assetId) {
            this.lastPath = assetId.path();
            return AssetMetadata.empty(assetId);
        }
    }
}
