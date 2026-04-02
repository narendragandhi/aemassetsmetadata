package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    @Override
    public Collection<AssetMetadata> findByPredicateValue(String predicateUri, String objectValue) {
        return store.values().stream()
                .filter(metadata -> hasStatement(metadata, predicateUri, objectValue))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<AssetMetadata> findRelated(AssetId assetId) {
        AssetMetadata original = store.get(assetId);
        if (original == null) {
            return java.util.Collections.emptyList();
        }

        // Suggest related if they share any predicate/object pair from the original asset
        return store.values().stream()
                .filter(metadata -> !metadata.assetId().equals(assetId)) // Skip self
                .filter(metadata -> hasOverlappingMetadata(original, metadata))
                .collect(Collectors.toList());
    }

    private boolean hasOverlappingMetadata(AssetMetadata original, AssetMetadata other) {
        return original.statements().stream()
                .anyMatch(s -> hasStatement(other, s.predicateUri(), s.objectValue()));
    }

    private boolean hasStatement(AssetMetadata metadata, String predicateUri, String objectValue) {
        return metadata.statements().stream()
                .anyMatch(s -> s.predicateUri().equals(predicateUri) && s.objectValue().equals(objectValue));
    }
}
