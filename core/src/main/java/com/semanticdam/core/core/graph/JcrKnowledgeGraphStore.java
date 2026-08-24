package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.apache.sling.api.resource.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class JcrKnowledgeGraphStore implements KnowledgeGraphService {
    private static final Logger LOG = LoggerFactory.getLogger(JcrKnowledgeGraphStore.class);
    private static final String GRAPH_ROOT = "/var/semanticdam/graph";
    private static final String STATEMENTS_NODE = "statements";
    private static final String PREDICATE = "predicate";
    private static final String OBJECT = "object";
    private static final String OBJECT_IS_URI = "objectIsUri";
    private static final String SOURCE = "source";
    private static final String VALID = "valid";
    private static final String CONFIDENCE = "confidence";
    
    private final ResourceResolverFactory resolverFactory;
    private final String serviceUser = "aem-assets-metadata-svc";

    public JcrKnowledgeGraphStore(ResourceResolverFactory resolverFactory) {
        this.resolverFactory = resolverFactory;
    }

    private ResourceResolver getResolver() throws LoginException {
        Map<String, Object> authInfo = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, serviceUser);
        return resolverFactory.getServiceResourceResolver(authInfo);
    }

    @Override
    public void writeAssetMetadata(AssetMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must be provided");
        }
        try (ResourceResolver resolver = getResolver()) {
            String path = GRAPH_ROOT + metadata.assetId().path();
            Resource resource = ResourceUtil.getOrCreateResource(resolver, path, "nt:unstructured", "nt:unstructured", false);
            String statementPath = path + "/" + STATEMENTS_NODE;
            Resource statementRoot = ResourceUtil.getOrCreateResource(
                    resolver, statementPath, "nt:unstructured", "nt:unstructured", false);
            for (Resource child : statementRoot.getChildren()) {
                resolver.delete(child);
            }
            int index = 0;
            for (MetadataStatement statement : metadata.statements()) {
                Map<String, Object> properties = new HashMap<>();
                properties.put(PREDICATE, statement.predicateUri());
                properties.put(OBJECT, statement.objectValue());
                properties.put(OBJECT_IS_URI, statement.objectIsUri());
                properties.put(SOURCE, statement.source());
                properties.put(VALID, statement.isValid());
                properties.put(CONFIDENCE, statement.confidenceScore());
                resolver.create(statementRoot, String.format("statement-%05d", ++index), properties);
            }
            resolver.commit();
        } catch (Exception e) {
            LOG.error("Failed to persist metadata to JCR for {}", metadata.assetId().path(), e);
        }
    }

    @Override
    public Optional<AssetMetadata> findByAssetId(AssetId assetId) {
        try (ResourceResolver resolver = getResolver()) {
            Resource resource = resolver.getResource(GRAPH_ROOT + assetId.path());
            if (resource == null) return Optional.empty();
            AssetMetadata metadata = AssetMetadata.empty(assetId);

            Resource statementRoot = resource.getChild(STATEMENTS_NODE);
            if (statementRoot == null) {
                return Optional.of(metadata);
            }
            for (Resource statementResource : statementRoot.getChildren()) {
                ValueMap vm = statementResource.getValueMap();
                String predicate = vm.get(PREDICATE, String.class);
                String object = vm.get(OBJECT, String.class);
                if (predicate != null && object != null) {
                    boolean objectIsUri = vm.get(OBJECT_IS_URI, false);
                    String source = vm.get(SOURCE, "JCR-Graph");
                    boolean valid = vm.get(VALID, true);
                    double confidence = vm.get(CONFIDENCE, 1.0);
                    metadata = metadata.add(objectIsUri
                            ? MetadataStatement.uri(predicate, object, source, valid, confidence)
                            : MetadataStatement.literal(predicate, object, source, valid, confidence));
                }
            }
            return Optional.of(metadata);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Collection<AssetMetadata> findByPredicateValue(String predicateUri, String objectValue) {
        if (predicateUri == null || predicateUri.isBlank() || objectValue == null || objectValue.isBlank()) {
            return Collections.emptyList();
        }
        String query = "SELECT * FROM [nt:unstructured] AS statement WHERE "
                + "ISDESCENDANTNODE(statement, '" + GRAPH_ROOT + "/') "
                + "AND [" + PREDICATE + "] = '" + escapeSql2(predicateUri) + "' "
                + "AND [" + OBJECT + "] = '" + escapeSql2(objectValue) + "'";
        try (ResourceResolver resolver = getResolver()) {
            Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(results, Spliterator.ORDERED), false)
                    .map(res -> assetMetadataForStatement(resolver, res).orElse(null))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Collection<AssetMetadata> findRelated(AssetId assetId) {
        Optional<AssetMetadata> original = findByAssetId(assetId);
        if (original.isEmpty()) return Collections.emptyList();

        // Cross-reference all statements to find overlaps
        Set<AssetMetadata> related = new HashSet<>();
        for (MetadataStatement statement : original.get().statements()) {
            related.addAll(findByPredicateValue(statement.predicateUri(), statement.objectValue()));
        }
        
        return related.stream()
                .filter(m -> !m.assetId().equals(assetId))
                .collect(Collectors.toList());
    }

    private Optional<AssetMetadata> assetMetadataForStatement(ResourceResolver resolver, Resource statement) {
        Resource statements = statement.getParent();
        Resource asset = statements == null ? null : statements.getParent();
        if (asset == null || !asset.getPath().startsWith(GRAPH_ROOT)) {
            return Optional.empty();
        }
        return findByAssetId(AssetId.of(asset.getPath().substring(GRAPH_ROOT.length())));
    }

    private String escapeSql2(String value) {
        return value.replace("'", "''");
    }
}
