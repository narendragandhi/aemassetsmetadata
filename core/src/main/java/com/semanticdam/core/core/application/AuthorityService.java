package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * AuthorityService implements the "Self-Healing" data pattern.
 * It uses Confidence Scores to ensure high-authority external data (PIM) 
 * overrides low-authority manual edits in AEM.
 */
@Component(service = AuthorityService.class)
public class AuthorityService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorityService.class);
    private static final String SERVICE_USER = "aem-assets-metadata-svc";

    @Reference
    private KnowledgeGraphService knowledgeGraphService;

    @Reference
    private ResourceResolverFactory resolverFactory;

    /**
     * Synchronizes a single metadata property back to the AEM Asset if the 
     * Knowledge Graph has higher authority.
     */
    public void enforceAuthority(AssetId assetId, String predicateUri, String jcrPropertyName) {
        Optional<AssetMetadata> metadataOpt = knowledgeGraphService.findByAssetId(assetId);
        if (metadataOpt.isEmpty()) return;

        Optional<MetadataStatement> authoritativeStatement = metadataOpt.get().statements().stream()
                .filter(s -> s.predicateUri().equals(predicateUri))
                .findFirst();

        if (authoritativeStatement.isEmpty()) return;

        double graphConfidence = authoritativeStatement.get().confidenceScore();

        Map<String, Object> authInfo = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_USER);
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(authInfo)) {
            Resource assetResource = resolver.getResource(assetId.path() + "/jcr:content/metadata");
            if (assetResource == null) return;

            ModifiableValueMap mvm = assetResource.adaptTo(ModifiableValueMap.class);
            if (mvm == null) return;

            String currentJcrValue = mvm.get(jcrPropertyName, String.class);
            String authoritativeValue = authoritativeStatement.get().objectValue();

            if (!authoritativeValue.equals(currentJcrValue)) {
                // In a world-class system, we only override if graph confidence > threshold (e.g. 0.8)
                if (graphConfidence >= 0.8) {
                    LOG.info("Authority Enforcement: Overriding {} for {} with high-confidence value from {}", 
                            jcrPropertyName, assetId.path(), authoritativeStatement.get().source());
                    mvm.put(jcrPropertyName, authoritativeValue);
                    resolver.commit();
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to enforce authority for asset {}", assetId.path(), e);
        }
    }
}
