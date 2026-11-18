package com.semanticdam.core.core.application;

import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import com.semanticdam.core.core.graph.JcrKnowledgeGraphStore;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves tags through the AEM {@link TagManager} with the SemanticDAM service user. */
@Component(service = TagResolver.class)
public class JcrTagResolver implements TagResolver {
    private final ResourceResolverFactory resolverFactory;

    @Activate
    public JcrTagResolver(@Reference ResourceResolverFactory resolverFactory) {
        this.resolverFactory = resolverFactory;
    }

    @Override
    public Map<String, TagInfo> resolve(Collection<String> tagIds) {
        Map<String, TagInfo> result = new LinkedHashMap<>();
        if (tagIds == null || tagIds.isEmpty()) {
            return result;
        }
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(
                Map.of(ResourceResolverFactory.SUBSERVICE, JcrKnowledgeGraphStore.SERVICE_USER))) {
            TagManager tagManager = resolver.adaptTo(TagManager.class);
            if (tagManager == null) {
                throw new IllegalStateException("TagManager is not available");
            }
            for (String tagId : tagIds) {
                Tag tag = tagManager.resolve(tagId);
                while (tag != null && !tag.isNamespace() && !result.containsKey(tag.getTagID())) {
                    Tag parent = tag.getParent();
                    String parentId = parent == null || parent.isNamespace() ? null : parent.getTagID();
                    Tag namespace = tag.getNamespace();
                    java.util.List<String> translations = new java.util.ArrayList<>();
                    java.util.Map<java.util.Locale, String> localized = tag.getLocalizedTitles();
                    if (localized != null) {
                        for (String title : localized.values()) {
                            if (title != null && !title.isBlank() && !title.equals(tag.getTitle()) && !translations.contains(title)) {
                                translations.add(title);
                            }
                        }
                    }
                    result.put(tag.getTagID(), new TagInfo(tag.getTagID(), tag.getTitle(), parentId,
                            namespace == null ? null : namespace.getName(),
                            namespace == null ? null : namespace.getTitle(), translations));
                    tag = parent;
                }
            }
            return result;
        } catch (LoginException e) {
            // Failing here makes the ingest job retry instead of storing tags without their hierarchy.
            throw new IllegalStateException("Service user cannot log in to resolve tags", e);
        }
    }
}
