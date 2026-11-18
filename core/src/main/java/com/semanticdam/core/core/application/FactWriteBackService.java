package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.ontology.Vocabulary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Computes the properties written back onto an asset's {@code jcr:content/metadata} node so that
 * governed facts travel with the asset: they replicate to publish, appear in the Assets metadata
 * editor and search, and can be read by pages through the visitor's own permissions.
 *
 * <p>For every predicate the property {@code semanticdam:<local name>} holds the authoritative,
 * valid values; links to entities are written as their labels. {@code semanticdam:status} is
 * {@code valid} or {@code violations}, and {@code semanticdam:ontologyVersion} records the model the
 * values were computed with. Properties of predicates that no longer have a value are removed.</p>
 */
public final class FactWriteBackService {
    public static final String PREFIX = "semanticdam:";
    public static final String STATUS = PREFIX + "status";
    public static final String ONTOLOGY_VERSION = PREFIX + "ontologyVersion";
    /** Written-back properties that are not facts. */
    static final Set<String> RESERVED = Set.of(STATUS, ONTOLOGY_VERSION);

    private FactWriteBackService() {
    }

    /** The {@code semanticdam:*} properties an asset should carry, in a stable order. */
    public static Map<String, String[]> properties(Collection<MetadataStatement> authoritative,
            Function<String, Optional<Entity>> entities, boolean hasViolations) {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        for (MetadataStatement statement : authoritative) {
            if (!statement.isValid() || Vocabulary.RDF_TYPE.equals(statement.predicateUri())) {
                continue;
            }
            String value = statement.objectIsUri()
                    ? entities.apply(statement.objectValue()).map(Entity::label).orElse(null)
                    : statement.objectValue();
            if (value != null && !value.isBlank()) {
                values.computeIfAbsent(PREFIX + localName(statement.predicateUri()), key -> new LinkedHashSet<>())
                        .add(value);
            }
        }
        Map<String, String[]> result = new LinkedHashMap<>();
        values.forEach((name, set) -> result.put(name, set.toArray(new String[0])));
        result.put(STATUS, new String[] {hasViolations ? "violations" : "valid"});
        return result;
    }

    /** Names of {@code semanticdam:*} properties on a node that are no longer wanted. */
    public static List<String> stale(Collection<String> existingNames, Map<String, String[]> wanted) {
        List<String> stale = new ArrayList<>();
        for (String name : existingNames) {
            if (name.startsWith(PREFIX) && !RESERVED.contains(name) && !wanted.containsKey(name)
                    && !name.equals(PREFIX + "assetType")) {
                stale.add(name);
            }
        }
        return stale;
    }

    static String localName(String predicateUri) {
        return predicateUri.substring(Math.max(predicateUri.lastIndexOf('#'), predicateUri.lastIndexOf('/')) + 1);
    }
}
