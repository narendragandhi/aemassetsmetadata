package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SlingResourceAemMetadataSource implements AemMetadataSource {
    private static final String METADATA_PATH = "/jcr:content/metadata";

    private final ResourceResolver resourceResolver;

    public SlingResourceAemMetadataSource(ResourceResolver resourceResolver) {
        if (resourceResolver == null) {
            throw new IllegalArgumentException("resourceResolver must be provided");
        }
        this.resourceResolver = resourceResolver;
    }

    @Override
    public Map<String, Object> readMetadata(AssetId assetId) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        String metadataPath = assetId.path() + METADATA_PATH;
        Resource metadataResource = resourceResolver.getResource(metadataPath);
        if (metadataResource == null) {
            return Collections.emptyMap();
        }
        ValueMap valueMap = metadataResource.getValueMap();
        return new HashMap<>(valueMap);
    }
}
