package com.semanticdam.core.core.application;

import com.day.cq.wcm.commons.utils.ReferenceSearch;
import com.semanticdam.core.core.contextvars.SemanticAssetPropertiesProvider;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.distribution.DistributionRequestType;
import org.apache.sling.distribution.DistributionResponse;
import org.apache.sling.distribution.Distributor;
import org.apache.sling.distribution.SimpleDistributionRequest;
import org.apache.sling.distribution.event.DistributionEventProperties;
import org.apache.sling.distribution.event.DistributionEventTopics;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps full-page caching while pages show governed facts through {@code semantic_properties}.
 *
 * <p>AEM's own reverse-reference invalidation does not cover DAM assets, so a page that renders an
 * asset's facts would stay cached with old values after the asset is republished. When an asset
 * has been distributed to publish, this component finds the pages that take their
 * {@code semantic_properties} from it and sends one explicit dispatcher {@code INVALIDATE} request
 * for them (deep, because child pages inherit the reference). Every other page keeps its cache.</p>
 *
 * <p>Author only (configuration in {@code config.author}). Runs as the dedicated sub-service
 * {@code semanticdam-invalidation}, the only SemanticDAM user allowed to replicate.</p>
 */
@Component(service = {PageCacheInvalidation.class, EventHandler.class, JobConsumer.class},
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {
                EventConstants.EVENT_TOPIC + "=" + DistributionEventTopics.AGENT_PACKAGE_DISTRIBUTED,
                JobConsumer.PROPERTY_TOPICS + "=" + PageCacheInvalidation.TOPIC
        })
@Designate(ocd = PageCacheInvalidation.Config.class)
public class PageCacheInvalidation implements EventHandler, JobConsumer {
    public static final String TOPIC = "com/semanticdam/page/invalidate";
    static final String PROPERTY_ASSETS = "assets";
    static final String SUBSERVICE = "semanticdam-invalidation";
    private static final Logger LOG = LoggerFactory.getLogger(PageCacheInvalidation.class);

    @ObjectClassDefinition(name = "SemanticDAM - Invalidate pages that show an asset's facts")
    public @interface Config {
        @AttributeDefinition(name = "Distribution agent", description = "Agent that receives the INVALIDATE request.")
        String agent() default "publish";

        @AttributeDefinition(name = "Page reference properties",
                description = "Same as the semantic_properties provider: properties of a page's jcr:content naming its asset.")
        String[] pageReferences() default {"semanticdam:asset", "cq:featuredimage/fileReference"};
    }

    private final ResourceResolverFactory resolverFactory;
    private final Distributor distributor;
    private final JobManager jobManager;
    private final String agent;
    private final SemanticAssetPropertiesProvider pages;

    @Activate
    public PageCacheInvalidation(@Reference ResourceResolverFactory resolverFactory, @Reference Distributor distributor,
            @Reference JobManager jobManager, Config config) {
        this.resolverFactory = resolverFactory;
        this.distributor = distributor;
        this.jobManager = jobManager;
        this.agent = config.agent();
        this.pages = new SemanticAssetPropertiesProvider(List.of(config.pageReferences()));
    }

    /** Queues invalidation for the pages that show these assets' facts. */
    public void queue(List<String> assetPaths) {
        List<String> assets = assetPaths.stream().filter(path -> path != null && path.startsWith("/content/dam/")).toList();
        if (!assets.isEmpty()) {
            jobManager.addJob(TOPIC, Map.of(PROPERTY_ASSETS, assets.toArray(new String[0])));
        }
    }

    @Override
    public void handleEvent(Event event) {
        // Only content that was added on publish; our own INVALIDATE requests also raise this event.
        if (!DistributionRequestType.ADD.name().equals(String.valueOf(event.getProperty(DistributionEventProperties.DISTRIBUTION_TYPE)))) {
            return;
        }
        Object paths = event.getProperty(DistributionEventProperties.DISTRIBUTION_PATHS);
        if (paths instanceof String[] array) {
            queue(Arrays.asList(array));
        }
    }

    @Override
    public JobResult process(Job job) {
        String[] assets = job.getProperty(PROPERTY_ASSETS, String[].class);
        if (assets == null || assets.length == 0) {
            return JobResult.CANCEL;
        }
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(
                Map.of(ResourceResolverFactory.SUBSERVICE, SUBSERVICE))) {
            List<String> pagePaths = new ArrayList<>(referencingPages(resolver, assets));
            if (pagePaths.isEmpty()) {
                return JobResult.OK;
            }
            DistributionResponse response = distributor.distribute(agent, resolver,
                    new SimpleDistributionRequest(DistributionRequestType.INVALIDATE, true, pagePaths.toArray(new String[0])));
            if (response == null || !response.isSuccessful()) {
                LOG.warn("Invalidation of {} for {} was not accepted by agent '{}': {}", pagePaths,
                        Arrays.toString(assets), agent, response == null ? "no response" : response.getMessage());
                return JobResult.FAILED;
            }
            LOG.info("Invalidated {} page(s) showing facts of {}: {}", pagePaths.size(), Arrays.toString(assets), pagePaths);
            return JobResult.OK;
        } catch (LoginException | RuntimeException e) {
            LOG.warn("Page invalidation for {} failed; it will be retried", Arrays.toString(assets), e);
            return JobResult.FAILED;
        }
    }

    /** Pages whose own jcr:content names one of the assets (children inherit; the request is deep). */
    Set<String> referencingPages(ResourceResolver resolver, String[] assets) {
        Set<String> result = new LinkedHashSet<>();
        for (String asset : assets) {
            ReferenceSearch search = new ReferenceSearch();
            search.setExact(true);
            search.setHollow(true);
            search.setMaxReferencesPerPage(1);
            for (String pagePath : search.search(resolver, asset).keySet()) {
                Resource page = resolver.getResource(pagePath);
                if (page != null && pages.pageReferences(page, asset)) {
                    result.add(pagePath);
                }
            }
        }
        return result;
    }
}
