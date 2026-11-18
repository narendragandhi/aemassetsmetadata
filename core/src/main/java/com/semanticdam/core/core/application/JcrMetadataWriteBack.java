package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.graph.JcrKnowledgeGraphStore;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Writes governed facts to {@code <asset>/jcr:content/metadata} as {@code semanticdam:*}
 * properties. Off unless configured. The write triggers one more ingest of the asset (the listener
 * sees a metadata change); that ingest computes the same values and writes nothing, so it settles.
 */
@Component(service = MetadataWriteBack.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = JcrMetadataWriteBack.Config.class)
public class JcrMetadataWriteBack implements MetadataWriteBack {

    @ObjectClassDefinition(name = "SemanticDAM - Write governed facts to asset metadata",
            description = "Needed for semantic_properties on publish and for AEM search to see governed facts.")
    public @interface Config {
        @AttributeDefinition(name = "Enabled")
        boolean enabled() default true;
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(JcrMetadataWriteBack.class);
    private final ResourceResolverFactory resolverFactory;
    private final boolean enabled;

    @Activate
    public JcrMetadataWriteBack(@Reference ResourceResolverFactory resolverFactory, Config config) {
        this.resolverFactory = resolverFactory;
        this.enabled = config.enabled();
    }

    private static String[] current(Node node, String name) throws RepositoryException {
        if (!node.hasProperty(name)) {
            return null;
        }
        Property property = node.getProperty(name);
        if (!property.isMultiple()) {
            return new String[] {property.getString()};
        }
        Value[] values = property.getValues();
        String[] strings = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            strings[i] = values[i].getString();
        }
        return strings;
    }

    @Override
    public boolean write(AssetId assetId, Map<String, String[]> properties, String ontologyVersion) {
        if (!enabled) {
            return false;
        }
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(
                Map.of(ResourceResolverFactory.SUBSERVICE, JcrKnowledgeGraphStore.SERVICE_USER))) {
            Resource metadata = resolver.getResource(assetId.path() + "/jcr:content/metadata");
            Node node = metadata == null ? null : metadata.adaptTo(Node.class);
            if (node == null) {
                // An asset without a metadata node has no facts to carry; nothing to write.
                return false;
            }
            // The JCR API is used instead of ModifiableValueMap because the service user may only
            // modify semanticdam:* properties, not the node as a whole, and Sling refuses a
            // ModifiableValueMap in that case. Oak checks each property against the ACL.
            boolean changed = false;
            for (Map.Entry<String, String[]> entry : properties.entrySet()) {
                if (!Arrays.equals(current(node, entry.getKey()), entry.getValue())) {
                    node.setProperty(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            String[] version = {ontologyVersion};
            if (!Arrays.equals(current(node, FactWriteBackService.ONTOLOGY_VERSION), version)) {
                node.setProperty(FactWriteBackService.ONTOLOGY_VERSION, ontologyVersion);
                changed = true;
            }
            List<String> names = new ArrayList<>();
            for (PropertyIterator iterator = node.getProperties(FactWriteBackService.PREFIX + "*"); iterator.hasNext(); ) {
                names.add(iterator.nextProperty().getName());
            }
            for (String stale : FactWriteBackService.stale(names, properties)) {
                node.getProperty(stale).remove();
                changed = true;
            }
            if (changed) {
                node.getSession().save();
                LOG.debug("Wrote governed facts to {}", metadata.getPath());
            }
            return changed;
        } catch (LoginException | RepositoryException | RuntimeException e) {
            throw new IllegalStateException("Failed to write governed facts to " + assetId.path(), e);
        }
    }
}
