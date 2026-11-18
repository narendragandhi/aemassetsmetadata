package com.semanticdam.core.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Optional;

/**
 * Renders an asset's authoritative schema.org facts as a JSON-LD {@code ImageObject}.
 *
 * <p>The result is a JSON document, not markup. It is safe to place inside a
 * {@code <script type="application/ld+json">} element: {@code <}, {@code >} and {@code &} are
 * emitted as unicode escapes, so a metadata value can never close the script element.</p>
 */
@Component(service = SemanticSeoService.class)
public class SemanticSeoService {

    @Reference
    private KnowledgeGraphService knowledgeGraphService;

    @Reference
    private ConflictResolutionService conflictResolutionService;

    public SemanticSeoService() {
    }

    SemanticSeoService(KnowledgeGraphService knowledgeGraphService, ConflictResolutionService conflictResolutionService) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.conflictResolutionService = conflictResolutionService;
    }

    /** @return the JSON-LD document, or an empty string if the asset is not in the graph */
    public String getJsonLd(String assetPath) {
        Optional<AssetMetadata> metadata = knowledgeGraphService.findByAssetId(AssetId.of(assetPath));
        if (metadata.isEmpty()) {
            return "";
        }
        JsonObject document = new JsonObject();
        document.addProperty("@context", "https://schema.org");
        document.addProperty("@type", "ImageObject");
        document.addProperty("contentUrl", assetPath);
        for (MetadataStatement statement : conflictResolutionService.resolve(metadata.get())) {
            if (!statement.isValid()) {
                continue;
            }
            String name;
            if (statement.predicateUri().startsWith(Vocabulary.SCHEMA)) {
                name = statement.predicateUri().substring(Vocabulary.SCHEMA.length());
            } else if (Vocabulary.TITLE.equals(statement.predicateUri())) {
                name = "name";
            } else if (Vocabulary.DESCRIPTION.equals(statement.predicateUri())) {
                name = "description";
            } else if (Vocabulary.FORMAT.equals(statement.predicateUri())) {
                name = "encodingFormat";
            } else {
                continue;
            }
            add(document, name, value(statement));
        }
        return document.toString().replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }

    private JsonElement value(MetadataStatement statement) {
        if (!statement.objectIsUri()) {
            return new com.google.gson.JsonPrimitive(statement.objectValue());
        }
        JsonObject node = new JsonObject();
        node.addProperty("@id", statement.objectValue());
        Optional<Entity> entity = knowledgeGraphService.findEntity(statement.objectValue());
        if (entity.isPresent()) {
            String type = entity.get().typeUri();
            if (type.startsWith(Vocabulary.SCHEMA)) {
                node.addProperty("@type", type.substring(Vocabulary.SCHEMA.length()));
            }
            node.addProperty("name", entity.get().label());
        }
        return node;
    }

    private static void add(JsonObject document, String name, JsonElement value) {
        JsonElement existing = document.get(name);
        if (existing == null) {
            document.add(name, value);
        } else if (existing.isJsonArray()) {
            existing.getAsJsonArray().add(value);
        } else {
            JsonArray values = new JsonArray();
            values.add(existing);
            values.add(value);
            document.add(name, values);
        }
    }
}
