package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.ontology.Vocabulary;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Transient store for tests and local development. Contents are lost on restart. */
public class InMemoryKnowledgeGraphStore implements KnowledgeGraphService {
    private final Map<AssetId, AssetMetadata> assets = new ConcurrentHashMap<>();
    private final Map<String, Entity> entities = new ConcurrentHashMap<>();

    @Override
    public void writeAssetMetadata(AssetMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must be provided");
        }
        assets.put(metadata.assetId(), metadata);
    }

    @Override
    public void deleteAsset(AssetId assetId) {
        assets.remove(assetId);
    }

    @Override
    public Optional<AssetMetadata> findByAssetId(AssetId assetId) {
        return Optional.ofNullable(assets.get(assetId));
    }

    @Override
    public Collection<AssetMetadata> findByPredicateValue(String predicateUri, String objectValue) {
        return assets.values().stream()
                .filter(metadata -> metadata.statements().stream().anyMatch(statement ->
                        statement.predicateUri().equals(predicateUri) && statement.objectValue().equals(objectValue)))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<AssetMetadata> findReferencing(String entityUri) {
        return assets.values().stream()
                .filter(metadata -> metadata.statements().stream().anyMatch(statement -> statement.objectIsUri()
                        && !Vocabulary.RDF_TYPE.equals(statement.predicateUri())
                        && statement.objectValue().equals(entityUri)))
                .collect(Collectors.toList());
    }

    @Override
    public void writeEntity(Entity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must be provided");
        }
        entities.put(entity.uri(), entity);
    }

    @Override
    public Optional<Entity> findEntity(String entityUri) {
        return Optional.ofNullable(entities.get(entityUri));
    }

    @Override
    public Collection<Entity> findEntities(Entity.Status status, String typeUri) {
        return entities.values().stream()
                .filter(entity -> entity.status() == status && (typeUri == null || typeUri.equals(entity.typeUri())))
                .sorted(java.util.Comparator.comparing(Entity::uri))
                .toList();
    }
}
