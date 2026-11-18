package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.ontology.Vocabulary;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Storage port for the asset graph: asset nodes with their statements, and the entity nodes
 * (brands, products, campaigns, concepts) those statements point at.
 *
 * <p>Implementations throw {@link GraphStoreException} when the store cannot be reached. An empty
 * result always means "nothing stored", never "the lookup failed".</p>
 */
public interface KnowledgeGraphService {
    /** Replaces every statement stored for the asset. */
    void writeAssetMetadata(AssetMetadata metadata);

    /** Removes the asset node. Entities it referenced are kept. */
    void deleteAsset(AssetId assetId);

    Optional<AssetMetadata> findByAssetId(AssetId assetId);

    /** Assets carrying a statement with exactly this predicate and object. */
    Collection<AssetMetadata> findByPredicateValue(String predicateUri, String objectValue);

    /** Assets linked to an entity node through any predicate. */
    Collection<AssetMetadata> findReferencing(String entityUri);

    /** Creates or updates an entity node. */
    void writeEntity(Entity entity);

    Optional<Entity> findEntity(String entityUri);

    /** Vocabulary entries with the given status, optionally only of one class (null for all). */
    Collection<Entity> findEntities(Entity.Status status, String typeUri);

    /**
     * Assets one hop away: those that share at least one entity with the given asset. Shared
     * literal values (a mime type, a title) do not make assets related.
     */
    default Collection<AssetMetadata> findRelated(AssetId assetId) {
        Optional<AssetMetadata> origin = findByAssetId(assetId);
        if (origin.isEmpty()) {
            return java.util.List.of();
        }
        Map<AssetId, AssetMetadata> related = new LinkedHashMap<>();
        for (MetadataStatement statement : origin.get().statements()) {
            if (!statement.objectIsUri() || Vocabulary.RDF_TYPE.equals(statement.predicateUri())) {
                continue;
            }
            for (AssetMetadata other : findReferencing(statement.objectValue())) {
                if (!other.assetId().equals(assetId)) {
                    related.putIfAbsent(other.assetId(), other);
                }
            }
        }
        return java.util.List.copyOf(related.values());
    }
}
