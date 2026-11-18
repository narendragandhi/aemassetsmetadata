package com.semanticdam.core.core.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Looks up AEM tags so that tag IDs on assets can become taxonomy concepts. */
@FunctionalInterface
public interface TagResolver {
    /**
     * An AEM tag below its namespace.
     *
     * @param parentId          null for a top-level tag
     * @param namespaceId       the namespace, e.g. {@code wknd-shared}; becomes the concept scheme
     * @param namespaceTitle    the namespace title
     * @param translatedTitles  localized titles other than the default; become alternative labels
     */
    record TagInfo(String tagId, String title, String parentId, String namespaceId, String namespaceTitle,
            List<String> translatedTitles) {
        public TagInfo {
            translatedTitles = translatedTitles == null ? List.of() : List.copyOf(translatedTitles);
        }

        public TagInfo(String tagId, String title, String parentId) {
            this(tagId, title, parentId, tagId.contains(":") ? tagId.substring(0, tagId.indexOf(':')) : null, null, List.of());
        }
    }

    /** Resolver that knows no tags; tag IDs are then used as labels. */
    TagResolver NONE = tagIds -> Map.of();

    /**
     * Resolves the given tags and all of their ancestors below the namespace.
     *
     * @return tag ID to tag, for the tags that exist
     */
    Map<String, TagInfo> resolve(Collection<String> tagIds);
}
