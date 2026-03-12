package com.example.aemassets.core.graph;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryKnowledgeGraphStore implements KnowledgeGraphService {
    private final Map<AssetId, AssetMetadata> store = new ConcurrentHashMap<>();

    @Override
    public void writeAssetMetadata(AssetMetadata metadata) {
        store.put(metadata.assetId(), metadata);
    }

    @Override
    public Optional<AssetMetadata> findByAssetId(AssetId assetId) {
        return Optional.ofNullable(store.get(assetId));
    }
}
