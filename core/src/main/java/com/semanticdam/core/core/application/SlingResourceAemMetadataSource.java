package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
    public Optional<Map<String, Object>> readMetadata(AssetId assetId) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        if (resourceResolver.getResource(assetId.path()) == null) {
            return Optional.empty();
        }
        Resource metadataResource = resourceResolver.getResource(assetId.path() + METADATA_PATH);
        if (metadataResource == null) {
            return Optional.of(Collections.emptyMap());
        }
        ValueMap valueMap = metadataResource.getValueMap();
        return Optional.of(new HashMap<>(valueMap));
    }
}
