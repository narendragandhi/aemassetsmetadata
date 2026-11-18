package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JCR proposal store; proposals are intentionally outside the graph path. */
@Component(service = AiProposalStore.class)
public final class JcrAiProposalStore implements AiProposalStore {
    private static final Logger LOG = LoggerFactory.getLogger(JcrAiProposalStore.class);
    private static final String ROOT = "/var/semanticdam/proposals";
    private final ResourceResolverFactory resolverFactory;
    private final String serviceUser;

    @Activate
    public JcrAiProposalStore(@Reference ResourceResolverFactory resolverFactory) {
        this(resolverFactory, com.semanticdam.core.core.graph.JcrKnowledgeGraphStore.SERVICE_USER);
    }

    JcrAiProposalStore(ResourceResolverFactory resolverFactory, String serviceUser) {
        if (resolverFactory == null || serviceUser == null || serviceUser.isBlank()) {
            throw new IllegalArgumentException("resolverFactory and serviceUser must be provided");
        }
        this.resolverFactory = resolverFactory;
        this.serviceUser = serviceUser;
    }

    @Override
    public void save(AiEnrichmentProposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal must be provided");
        }
        try (ResourceResolver resolver = getResolver()) {
            String assetPath = ROOT + proposal.assetId().path();
            ResourceUtil.getOrCreateResource(resolver, assetPath,
                    "nt:unstructured", "nt:unstructured", false);
            Resource proposalRoot = ResourceUtil.getOrCreateResource(resolver, assetPath + "/items",
                    "nt:unstructured", "nt:unstructured", false);
            Map<String, Object> values = new HashMap<>();
            values.put("predicate", proposal.statement().predicateUri());
            values.put("object", proposal.statement().objectValue());
            values.put("objectIsUri", proposal.statement().objectIsUri());
            values.put("source", proposal.statement().source());
            values.put("valid", proposal.statement().isValid());
            values.put("confidence", proposal.statement().confidenceScore());
            values.put("modelId", proposal.modelId());
            values.put("promptVersion", proposal.promptVersion());
            values.put("rationale", proposal.rationale());
            values.put("status", proposal.status().name());
            String proposalName = "proposal-" + Integer.toHexString(proposalKey(proposal));
            Resource record = proposalRoot.getChild(proposalName);
            if (record == null) {
                record = resolver.create(proposalRoot, proposalName, values);
            } else {
                ModifiableValueMap properties = record.adaptTo(ModifiableValueMap.class);
                if (properties == null) {
                    throw new IllegalStateException("proposal record is not writable");
                }
                properties.putAll(values);
            }
            resolver.commit();
        } catch (Exception e) {
            LOG.error("Failed to persist AI proposal for {}", proposal.assetId().path(), e);
            throw new IllegalStateException("failed to persist AI proposal", e);
        }
    }

    @Override
    public List<AiEnrichmentProposal> findByAsset(AssetId assetId) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        try (ResourceResolver resolver = getResolver()) {
            Resource proposalRoot = resolver.getResource(ROOT + assetId.path() + "/items");
            if (proposalRoot == null) {
                return List.of();
            }
            List<AiEnrichmentProposal> result = new ArrayList<>();
            for (Resource resource : proposalRoot.getChildren()) {
                ValueMap values = resource.getValueMap();
                MetadataStatement statement = values.get("objectIsUri", false)
                        ? MetadataStatement.uri(values.get("predicate", String.class), values.get("object", String.class),
                        values.get("source", "AI"), values.get("valid", true), values.get("confidence", 1.0))
                        : MetadataStatement.literal(values.get("predicate", String.class), values.get("object", String.class),
                        values.get("source", "AI"), values.get("valid", true), values.get("confidence", 1.0));
                AiEnrichmentProposal proposal = AiEnrichmentProposal.proposed(assetId, statement,
                        values.get("modelId", String.class), values.get("promptVersion", String.class),
                        values.get("rationale", String.class));
                String status = values.get("status", AiEnrichmentProposal.Status.PROPOSED.name());
                if (AiEnrichmentProposal.Status.APPROVED.name().equals(status)) {
                    proposal = proposal.approve();
                } else if (AiEnrichmentProposal.Status.REJECTED.name().equals(status)) {
                    proposal = proposal.reject();
                }
                result.add(proposal);
            }
            return List.copyOf(result);
        } catch (LoginException | RuntimeException e) {
            // An unreadable store must not look like "no proposals": that would allow re-proposing.
            throw new IllegalStateException("failed to read AI proposals for " + assetId.path(), e);
        }
    }

    @Override
    public void recordAudit(AiProposalAuditEvent event, AiEnrichmentProposal proposal) {
        if (event == null || proposal == null) {
            throw new IllegalArgumentException("event and proposal must be provided");
        }
        try (ResourceResolver resolver = getResolver()) {
            Resource auditRoot = ResourceUtil.getOrCreateResource(resolver,
                    ROOT + event.assetId().path() + "/audit", "nt:unstructured", "nt:unstructured", false);
            Map<String, Object> values = new HashMap<>();
            values.put("status", event.status().name());
            values.put("actor", event.actor());
            values.put("occurredAt", event.occurredAt().toString());
            values.put("reason", event.reason());
            values.put("predicate", proposal.statement().predicateUri());
            values.put("object", proposal.statement().objectValue());
            values.put("modelId", proposal.modelId());
            values.put("promptVersion", proposal.promptVersion());
            resolver.create(auditRoot, ResourceUtil.createUniqueChildName(auditRoot,
                    "event-" + event.occurredAt().toEpochMilli()), values);
            resolver.commit();
        } catch (LoginException | PersistenceException | RuntimeException e) {
            throw new IllegalStateException("failed to record AI review audit event", e);
        }
    }

    @Override
    public List<AiProposalAuditEvent> auditTrail(AssetId assetId) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        try (ResourceResolver resolver = getResolver()) {
            Resource auditRoot = resolver.getResource(ROOT + assetId.path() + "/audit");
            if (auditRoot == null) {
                return List.of();
            }
            List<AiProposalAuditEvent> events = new ArrayList<>();
            for (Resource resource : auditRoot.getChildren()) {
                ValueMap values = resource.getValueMap();
                events.add(new AiProposalAuditEvent(assetId,
                        AiEnrichmentProposal.Status.valueOf(values.get("status", String.class)),
                        values.get("actor", String.class),
                        java.time.Instant.parse(values.get("occurredAt", String.class)),
                        values.get("reason", String.class)));
            }
            events.sort(java.util.Comparator.comparing(AiProposalAuditEvent::occurredAt));
            return List.copyOf(events);
        } catch (LoginException | RuntimeException e) {
            throw new IllegalStateException("failed to read AI review audit trail for " + assetId.path(), e);
        }
    }

    private ResourceResolver getResolver() throws LoginException {
        return resolverFactory.getServiceResourceResolver(
                Map.of(ResourceResolverFactory.SUBSERVICE, serviceUser));
    }

    private int proposalKey(AiEnrichmentProposal proposal) {
        return java.util.Objects.hash(proposal.assetId(), proposal.statement(), proposal.modelId(),
                proposal.promptVersion(), proposal.rationale());
    }
}
