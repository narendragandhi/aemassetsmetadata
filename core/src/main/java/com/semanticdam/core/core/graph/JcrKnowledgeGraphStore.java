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
        try (ResourceResolver resolver = getResolver()) {
            String path = GRAPH_ROOT + metadata.assetId().path();
            Resource resource = ResourceUtil.getOrCreateResource(resolver, path, "nt:unstructured", "nt:unstructured", false);
            ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);
            
            if (mvm != null) {
                // Store metadata as a JSON-like structure or flat properties for simplicity
                for (MetadataStatement statement : metadata.statements()) {
                    String propName = encodePredicate(statement.predicateUri());
                    mvm.put(propName, statement.objectValue());
                }
                resolver.commit();
            }
        } catch (Exception e) {
            LOG.error("Failed to persist metadata to JCR for {}", metadata.assetId().path(), e);
        }
    }

    @Override
    public Optional<AssetMetadata> findByAssetId(AssetId assetId) {
        try (ResourceResolver resolver = getResolver()) {
            Resource resource = resolver.getResource(GRAPH_ROOT + assetId.path());
            if (resource == null) return Optional.empty();

            ValueMap vm = resource.getValueMap();
            AssetMetadata metadata = AssetMetadata.empty(assetId);
            
            for (String key : vm.keySet()) {
                if (key.contains("_at_")) { // Our encoded predicate marker
                    String uri = decodePredicate(key);
                    metadata = metadata.add(MetadataStatement.literal(uri, vm.get(key, String.class), "JCR-Graph", true));
                }
            }
            return Optional.of(metadata);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Collection<AssetMetadata> findByPredicateValue(String predicateUri, String objectValue) {
        // In JCR, we use XPath or SQL2 for high-speed graph traversal
        String query = String.format("/jcr:root%s//*[%s = '%s']", GRAPH_ROOT, encodePredicate(predicateUri), objectValue);
        try (ResourceResolver resolver = getResolver()) {
            Iterator<Resource> results = resolver.findResources(query, "xpath");
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(results, Spliterator.ORDERED), false)
                    .map(res -> findByAssetId(AssetId.of(res.getPath().substring(GRAPH_ROOT.length()))).orElse(null))
                    .filter(Objects::nonNull)
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

    private String encodePredicate(String uri) {
        return "p_at_" + uri.replace("://", "_").replace("/", "_").replace("#", "_").replace(".", "_");
    }

    private String decodePredicate(String key) {
        // In a real system, we'd use an ID-to-URI mapping table
        return key.substring(5); 
    }
}
