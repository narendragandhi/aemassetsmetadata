package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Component(service = AemMetadataSource.class)
public class SlingResourceAemMetadataSourceService implements AemMetadataSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlingResourceAemMetadataSourceService.class);

    @ObjectClassDefinition(name = "AEM Assets Metadata - Sling Metadata Source")
    public @interface Config {
        @AttributeDefinition(name = "Service User", description = "Subservice name for service user mapping.")
        String serviceUser() default "aemassetsmetadata";
    }

    private final ResourceResolverFactory resourceResolverFactory;
    private Config config;

    public SlingResourceAemMetadataSourceService(
            @Reference ResourceResolverFactory resourceResolverFactory) {
        this.resourceResolverFactory = resourceResolverFactory;
    }

    @Activate
    @Modified
    protected void activate(Config config) {
        this.config = config;
    }

    @Override
    public Map<String, Object> readMetadata(AssetId assetId) {
        Map<String, Object> authInfo = Map.of(
                ResourceResolverFactory.SUBSERVICE, config.serviceUser()
        );
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            SlingResourceAemMetadataSource delegate = new SlingResourceAemMetadataSource(resolver);
            return delegate.readMetadata(assetId);
        } catch (LoginException ex) {
            LOGGER.warn("Failed to obtain service resolver for asset metadata", ex);
            return java.util.Collections.emptyMap();
        }
    }
}
