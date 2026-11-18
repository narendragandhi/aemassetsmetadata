package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores the graph in the repository.
 *
 * <pre>
 * /var/semanticdam/graph/&lt;asset path&gt;/statements/statement-00001   one node per statement
 * /var/semanticdam/entities/&lt;2 hex&gt;/&lt;sha-256 of IRI&gt;             one node per entity
 * </pre>
 *
 * Statement lookups rely on the {@code semanticdam.statement-custom-1} Lucene index shipped in
 * ui.apps; without it they fall back to a traversal.
 */
public class JcrKnowledgeGraphStore implements KnowledgeGraphService {
    /** Sub-service name; mapped to the aem-assets-metadata-svc system user in ui.config. */
    public static final String SERVICE_USER = "semanticdam";
    static final String GRAPH_ROOT = "/var/semanticdam/graph";
    static final String ENTITY_ROOT = "/var/semanticdam/entities";
    static final String STATEMENT_RESOURCE_TYPE = "semanticdam/graph/statement";
    static final String ENTITY_RESOURCE_TYPE = "semanticdam/graph/entity";
    private static final String STATEMENTS_NODE = "statements";
    private static final String RESOURCE_TYPE = "sling:resourceType";
    private static final String PREDICATE = "predicate";
    private static final String OBJECT = "object";
    private static final String OBJECT_IS_URI = "objectIsUri";
    private static final String SOURCE = "source";
    private static final String VALID = "valid";
    private static final String CONFIDENCE = "confidence";
    private static final String FOLDER = "sling:Folder";
    private static final String NODE = "nt:unstructured";

    private final ResourceResolverFactory resolverFactory;

    public JcrKnowledgeGraphStore(ResourceResolverFactory resolverFactory) {
        if (resolverFactory == null) {
            throw new IllegalArgumentException("resolverFactory must be provided");
        }
        this.resolverFactory = resolverFactory;
    }

    @Override
    public void writeAssetMetadata(AssetMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must be provided");
        }
        String path = GRAPH_ROOT + metadata.assetId().path();
        try (ResourceResolver resolver = resolver()) {
            ResourceUtil.getOrCreateResource(resolver, path, NODE, NODE, false);
            Resource statementRoot = ResourceUtil.getOrCreateResource(
                    resolver, path + "/" + STATEMENTS_NODE, NODE, NODE, false);
            for (Resource child : statementRoot.getChildren()) {
                resolver.delete(child);
            }
            int index = 0;
            for (MetadataStatement statement : metadata.statements()) {
                Map<String, Object> properties = new HashMap<>();
                properties.put(RESOURCE_TYPE, STATEMENT_RESOURCE_TYPE);
                properties.put(PREDICATE, statement.predicateUri());
                properties.put(OBJECT, statement.objectValue());
                properties.put(OBJECT_IS_URI, statement.objectIsUri());
                properties.put(SOURCE, statement.source());
                properties.put(VALID, statement.isValid());
                properties.put(CONFIDENCE, statement.confidenceScore());
                resolver.create(statementRoot, String.format("statement-%05d", ++index), properties);
            }
            // Deletes and creates are committed together, so readers never see a half-written asset.
            resolver.commit();
        } catch (LoginException | PersistenceException | RuntimeException e) {
            throw new GraphStoreException("Failed to write graph metadata for " + metadata.assetId().path(), e);
        }
    }

    @Override
    public void deleteAsset(AssetId assetId) {
        try (ResourceResolver resolver = resolver()) {
            Resource resource = resolver.getResource(GRAPH_ROOT + assetId.path());
            if (resource != null) {
                resolver.delete(resource);
                resolver.commit();
            }
        } catch (LoginException | PersistenceException | RuntimeException e) {
            throw new GraphStoreException("Failed to delete graph metadata for " + assetId.path(), e);
        }
    }

    @Override
    public Optional<AssetMetadata> findByAssetId(AssetId assetId) {
        try (ResourceResolver resolver = resolver()) {
            return read(resolver, assetId);
        } catch (LoginException | RuntimeException e) {
            throw new GraphStoreException("Failed to read graph metadata for " + assetId.path(), e);
        }
    }

    @Override
    public Collection<AssetMetadata> findByPredicateValue(String predicateUri, String objectValue) {
        if (predicateUri == null || predicateUri.isBlank() || objectValue == null || objectValue.isBlank()) {
            return List.of();
        }
        return query("AND statement.[" + PREDICATE + "] = '" + escape(predicateUri) + "' "
                + "AND statement.[" + OBJECT + "] = '" + escape(objectValue) + "'");
    }

    @Override
    public Collection<AssetMetadata> findReferencing(String entityUri) {
        if (entityUri == null || entityUri.isBlank()) {
            return List.of();
        }
        return query("AND statement.[" + OBJECT + "] = '" + escape(entityUri) + "' "
                + "AND statement.[" + OBJECT_IS_URI + "] = true "
                + "AND statement.[" + PREDICATE + "] <> '" + Vocabulary.RDF_TYPE + "'");
    }

    @Override
    public void writeEntity(Entity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must be provided");
        }
        String path = entityPath(entity.uri());
        try (ResourceResolver resolver = resolver()) {
            Resource existing = resolver.getResource(path);
            if (existing != null && entity.equals(readEntity(existing, entity.uri()))) {
                return;
            }
            Map<String, Object> properties = new HashMap<>();
            properties.put("uri", entity.uri());
            properties.put("type", entity.typeUri());
            properties.put("label", entity.label());
            properties.put("broader", entity.broader().toArray(new String[0]));
            properties.put("altLabels", entity.altLabels().toArray(new String[0]));
            properties.put("hiddenLabels", entity.hiddenLabels().toArray(new String[0]));
            properties.put("status", entity.status().name());
            if (existing == null) {
                properties.put("jcr:primaryType", NODE);
                properties.put(RESOURCE_TYPE, ENTITY_RESOURCE_TYPE);
                putOptional(properties, "scheme", entity.scheme());
                putOptional(properties, "replacedBy", entity.replacedBy());
                ResourceUtil.getOrCreateResource(resolver, path, properties, FOLDER, false);
            } else {
                ModifiableValueMap values = existing.adaptTo(ModifiableValueMap.class);
                if (values == null) {
                    throw new PersistenceException("entity node is not writable: " + path);
                }
                values.putAll(properties);
                values.put(RESOURCE_TYPE, ENTITY_RESOURCE_TYPE);
                setOrRemove(values, "scheme", entity.scheme());
                setOrRemove(values, "replacedBy", entity.replacedBy());
            }
            resolver.commit();
        } catch (LoginException | PersistenceException | RuntimeException e) {
            throw new GraphStoreException("Failed to write entity " + entity.uri(), e);
        }
    }

    @Override
    public Optional<Entity> findEntity(String entityUri) {
        if (entityUri == null || entityUri.isBlank()) {
            return Optional.empty();
        }
        try (ResourceResolver resolver = resolver()) {
            Resource resource = resolver.getResource(entityPath(entityUri));
            return resource == null ? Optional.empty() : Optional.ofNullable(readEntity(resource, entityUri));
        } catch (LoginException | RuntimeException e) {
            throw new GraphStoreException("Failed to read entity " + entityUri, e);
        }
    }

    @Override
    public Collection<Entity> findEntities(Entity.Status status, String typeUri) {
        String statement = "SELECT * FROM [nt:unstructured] AS entity WHERE "
                + "ISDESCENDANTNODE(entity, '" + ENTITY_ROOT + "') "
                + "AND entity.[" + RESOURCE_TYPE + "] = '" + ENTITY_RESOURCE_TYPE + "' "
                + "AND entity.[status] = '" + status.name() + "' "
                + (typeUri == null ? "" : "AND entity.[type] = '" + escape(typeUri) + "' ")
                + "OPTION(INDEX TAG semanticdam)";
        try (ResourceResolver resolver = resolver()) {
            List<Entity> result = new ArrayList<>();
            Iterator<Resource> results = resolver.findResources(statement, "JCR-SQL2");
            while (results.hasNext()) {
                Resource resource = results.next();
                Entity entity = readEntity(resource, resource.getValueMap().get("uri", String.class));
                if (entity != null) {
                    result.add(entity);
                }
            }
            result.sort(java.util.Comparator.comparing(Entity::uri));
            return result;
        } catch (LoginException | RuntimeException e) {
            throw new GraphStoreException("Entity query failed", e);
        }
    }

    private static Entity readEntity(Resource resource, String uri) {
        ValueMap values = resource.getValueMap();
        String type = values.get("type", String.class);
        String label = values.get("label", String.class);
        if (uri == null || type == null || label == null) {
            return null;
        }
        return new Entity(uri, type, label, List.of(values.get("broader", new String[0])),
                List.of(values.get("altLabels", new String[0])), List.of(values.get("hiddenLabels", new String[0])),
                values.get("scheme", String.class),
                Entity.Status.valueOf(values.get("status", Entity.Status.APPROVED.name())),
                values.get("replacedBy", String.class));
    }

    private static void putOptional(Map<String, Object> properties, String name, String value) {
        if (value != null) {
            properties.put(name, value);
        }
    }

    private static void setOrRemove(ModifiableValueMap values, String name, String value) {
        if (value == null) {
            values.remove(name);
        } else {
            values.put(name, value);
        }
    }

    private Collection<AssetMetadata> query(String condition) {
        String statement = "SELECT * FROM [nt:unstructured] AS statement WHERE "
                + "ISDESCENDANTNODE(statement, '" + GRAPH_ROOT + "') "
                + "AND statement.[" + RESOURCE_TYPE + "] = '" + STATEMENT_RESOURCE_TYPE + "' "
                + condition + " OPTION(INDEX TAG semanticdam)";
        try (ResourceResolver resolver = resolver()) {
            Map<AssetId, AssetMetadata> matches = new LinkedHashMap<>();
            Iterator<Resource> results = resolver.findResources(statement, "JCR-SQL2");
            while (results.hasNext()) {
                Resource statements = results.next().getParent();
                Resource asset = statements == null ? null : statements.getParent();
                if (asset == null || !asset.getPath().startsWith(GRAPH_ROOT + "/")) {
                    continue;
                }
                AssetId assetId = AssetId.of(asset.getPath().substring(GRAPH_ROOT.length()));
                if (!matches.containsKey(assetId)) {
                    read(resolver, assetId).ifPresent(metadata -> matches.put(assetId, metadata));
                }
            }
            return List.copyOf(matches.values());
        } catch (LoginException | RuntimeException e) {
            throw new GraphStoreException("Graph query failed", e);
        }
    }

    private Optional<AssetMetadata> read(ResourceResolver resolver, AssetId assetId) {
        Resource resource = resolver.getResource(GRAPH_ROOT + assetId.path());
        Resource statementRoot = resource == null ? null : resource.getChild(STATEMENTS_NODE);
        if (statementRoot == null) {
            return Optional.empty();
        }
        List<MetadataStatement> statements = new ArrayList<>();
        for (Resource statementResource : statementRoot.getChildren()) {
            ValueMap values = statementResource.getValueMap();
            String predicate = values.get(PREDICATE, String.class);
            String object = values.get(OBJECT, String.class);
            if (predicate == null || object == null) {
                continue;
            }
            String source = values.get(SOURCE, "unknown");
            boolean valid = values.get(VALID, true);
            double confidence = values.get(CONFIDENCE, 1.0);
            statements.add(values.get(OBJECT_IS_URI, false)
                    ? MetadataStatement.uri(predicate, object, source, valid, confidence)
                    : MetadataStatement.literal(predicate, object, source, valid, confidence));
        }
        return Optional.of(new AssetMetadata(assetId, statements));
    }

    private ResourceResolver resolver() throws LoginException {
        return resolverFactory.getServiceResourceResolver(
                Map.of(ResourceResolverFactory.SUBSERVICE, SERVICE_USER));
    }

    static String entityPath(String entityUri) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(entityUri.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return ENTITY_ROOT + "/" + hex.substring(0, 2) + "/" + hex;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String escape(String value) {
        return value.replace("'", "''");
    }
}
