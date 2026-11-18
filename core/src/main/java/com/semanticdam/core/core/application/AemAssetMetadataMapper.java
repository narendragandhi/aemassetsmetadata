package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.domain.OntologyTerm;
import com.semanticdam.core.core.graph.OntologyRegistry;
import com.semanticdam.core.core.ontology.PropertyMapping;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns DAM metadata properties into graph statements using the {@code sdam:PropertyMapping}
 * declarations of the ontology. Nothing about individual properties is hardcoded here.
 */
@Component(service = AemAssetMetadataMapper.class)
public class AemAssetMetadataMapper {
    private final OntologyRegistry ontology;

    @Activate
    public AemAssetMetadataMapper(@Reference OntologyRegistry ontology) {
        if (ontology == null) {
            throw new IllegalArgumentException("ontology must be provided");
        }
        this.ontology = ontology;
    }

    public AssetMetadata map(AssetId assetId, Map<String, Object> metadata) {
        return extract(assetId, metadata).metadata();
    }

    public ExtractedAsset extract(AssetId assetId, Map<String, Object> metadata) {
        return extract(assetId, metadata, TagResolver.NONE, uri -> Optional.empty());
    }

    public ExtractedAsset extract(AssetId assetId, Map<String, Object> metadata, TagResolver tags) {
        return extract(assetId, metadata, tags, uri -> Optional.empty());
    }

    /**
     * @param stored looks up vocabulary entries already in the graph, so that steward decisions
     *               (approval, rejection, merges) are respected
     */
    public ExtractedAsset extract(AssetId assetId, Map<String, Object> metadata, TagResolver tags,
            java.util.function.Function<String, Optional<Entity>> stored) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must be provided");
        }
        List<MetadataStatement> statements = new ArrayList<>();
        Map<String, Entity> entities = new LinkedHashMap<>();
        Map<String, TagResolver.TagInfo> resolvedTags = resolveTags(metadata, tags);
        for (PropertyMapping mapping : ontology.mappings()) {
            String source = mapping.sourceSystem().label();
            double trust = mapping.sourceSystem().trust();
            for (String value : values(metadata.get(mapping.sourceProperty()))) {
                MetadataStatement statement;
                if (mapping.mapsToClass()) {
                    Optional<OntologyTerm> assetClass = ontology.findByLabel(value)
                            .filter(term -> term.kind() == OntologyTerm.Kind.CLASS)
                            .filter(term -> ontology.superClasses(term.uri()).contains(Vocabulary.ASSET));
                    if (assetClass.isEmpty()) {
                        continue;
                    }
                    statement = MetadataStatement.uri(mapping.predicateUri(), assetClass.get().uri(), source, true, trust);
                } else if (mapping.isAemTag()) {
                    Entity concept = tagConcept(mapping, value, resolvedTags, entities);
                    statement = MetadataStatement.uri(mapping.predicateUri(), concept.uri(), source, true, trust);
                } else if (mapping.entityClass().isPresent()) {
                    Entity entity = resolveEntity(mapping, value, stored);
                    entities.putIfAbsent(entity.uri(), entity);
                    statement = MetadataStatement.uri(mapping.predicateUri(), entity.uri(), source, true, trust);
                } else {
                    statement = MetadataStatement.literal(mapping.predicateUri(), value, source, true, trust);
                }
                if (statements.stream().noneMatch(existing -> existing.sameFact(statement)
                        && existing.source().equals(statement.source()))) {
                    statements.add(statement);
                }
            }
        }
        return new ExtractedAsset(new AssetMetadata(assetId, statements), entities);
    }

    private Map<String, TagResolver.TagInfo> resolveTags(Map<String, Object> metadata, TagResolver tags) {
        List<String> tagIds = new ArrayList<>();
        for (PropertyMapping mapping : ontology.mappings()) {
            if (mapping.isAemTag()) {
                tagIds.addAll(values(metadata.get(mapping.sourceProperty())));
            }
        }
        return tagIds.isEmpty() || tags == null ? Map.of() : tags.resolve(tagIds);
    }

    /**
     * Turns an AEM tag into a concept and records the concept and its ancestors as entities. A tag
     * that no longer exists keeps its ID as label and has no parent.
     */
    private Entity tagConcept(PropertyMapping mapping, String tagId, Map<String, TagResolver.TagInfo> tags,
            Map<String, Entity> entities) {
        String classUri = mapping.entityClass().orElse(Vocabulary.CONCEPT_CLASS);
        String namespace = mapping.instanceNamespace() != null ? mapping.instanceNamespace()
                : ontology.instanceNamespace(classUri).orElse("https://semanticdam.com/id/tag/");
        Entity first = null;
        String current = tagId;
        while (current != null) {
            TagResolver.TagInfo info = tags.get(current);
            String parent = info == null ? null : info.parentId();
            String title = info == null || info.title() == null || info.title().isBlank() ? current : info.title();
            String namespaceId = info != null && info.namespaceId() != null ? info.namespaceId()
                    : current.contains(":") ? current.substring(0, current.indexOf(':')) : null;
            String scheme = namespaceId == null ? null : tagIri(namespace, namespaceId + ":");
            if (scheme != null && !entities.containsKey(scheme)) {
                String schemeTitle = info != null && info.namespaceTitle() != null && !info.namespaceTitle().isBlank()
                        ? info.namespaceTitle() : namespaceId;
                entities.put(scheme, new Entity(scheme, Vocabulary.CONCEPT_SCHEME_CLASS, schemeTitle));
            }
            Entity entity = new Entity(tagIri(namespace, current), classUri, title,
                    parent == null ? List.of() : List.of(tagIri(namespace, parent)),
                    info == null ? List.of() : info.translatedTitles(), List.of(current), scheme,
                    Entity.Status.APPROVED, null);
            entities.putIfAbsent(entity.uri(), entity);
            first = first == null ? entity : first;
            current = parent;
        }
        return first;
    }

    static String tagIri(String namespace, String tagId) {
        // "wknd-shared:region/emea" -> <namespace>wknd-shared/region/emea
        StringBuilder path = new StringBuilder();
        for (String segment : tagId.replaceFirst(":", "/").split("/")) {
            if (!segment.isEmpty()) {
                path.append(path.length() == 0 ? "" : "/")
                        .append(java.net.URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }
        return namespace + path;
    }

    /**
     * Resolves a value against the controlled vocabulary, in order: a preferred, alternative or
     * hidden label declared in the ontology or profile; an entry already in the graph under the
     * label's IRI, following merges; otherwise a new entry, which is APPROVED only when the mapping's
     * source is the authority for this vocabulary and a CANDIDATE otherwise.
     */
    Entity resolveEntity(PropertyMapping mapping, String label,
            java.util.function.Function<String, Optional<Entity>> stored) {
        String classUri = mapping.entityClass().orElseThrow();
        String trimmed = label.trim();
        Optional<Entity> declared = ontology.findEntityByLabel(classUri, trimmed);
        if (declared.isPresent()) {
            return declared.get();
        }
        String namespace = ontology.instanceNamespace(classUri).orElse("https://semanticdam.com/id/entity/");
        String uri = namespace + slug(trimmed);
        Optional<Entity> existing = stored.apply(uri);
        for (int hops = 0; existing.isPresent() && existing.get().status() == Entity.Status.MERGED && hops < 10; hops++) {
            String target = existing.get().replacedBy();
            existing = ontology.findEntity(target).or(() -> stored.apply(target));
        }
        if (existing.isPresent()) {
            return existing.get();
        }
        return new Entity(uri, classUri, trimmed, List.of(), List.of(), List.of(), null,
                mapping.createsApprovedEntities() ? Entity.Status.APPROVED : Entity.Status.CANDIDATE, null);
    }

    static String slug(String label) {
        String ascii = Normalizer.normalize(label, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (!slug.isEmpty()) {
            return slug;
        }
        // Labels without any ASCII letter or digit still need a stable, IRI-safe identifier.
        StringBuilder hex = new StringBuilder("x");
        for (byte value : label.getBytes(StandardCharsets.UTF_8)) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private static List<String> values(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof Object[] array) {
            for (Object item : array) {
                addValue(result, item);
            }
        } else if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                addValue(result, item);
            }
        } else {
            addValue(result, raw);
        }
        return result;
    }

    private static void addValue(List<String> result, Object item) {
        if ((item instanceof String || item instanceof Number || item instanceof Boolean)
                && !item.toString().isBlank()) {
            result.add(item.toString().trim());
        }
    }
}
