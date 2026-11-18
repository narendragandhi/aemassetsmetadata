package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Collection;

@Component(service = AssetMetadataIndexingService.class)
public class AssetMetadataIndexingService {
    private final KnowledgeGraphService knowledgeGraphService;

    @Activate
    public AssetMetadataIndexingService(@Reference KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    public void index(AssetMetadata metadata) {
        knowledgeGraphService.writeAssetMetadata(metadata);
    }

    /**
     * Stores entity nodes that are new or have changed, so that a renamed or moved AEM tag updates
     * its concept. Entities are shared between assets: the last ingest that sees an entity sets it.
     */
    public void indexEntities(Collection<Entity> entities) {
        for (Entity entity : entities) {
            if (!knowledgeGraphService.findEntity(entity.uri()).filter(entity::equals).isPresent()) {
                knowledgeGraphService.writeEntity(entity);
            }
        }
    }

    public void remove(AssetId assetId) {
        knowledgeGraphService.deleteAsset(assetId);
    }
}
