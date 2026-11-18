package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.OntologyRegistry;
import com.semanticdam.core.core.ontology.InferenceRule;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Derives statements from the ontology, never from code:
 * <ul>
 *   <li>taxonomy: an asset about a concept is also about every ancestor, following {@code skos:broader}
 *       in the ontology and the parent links of AEM tags;</li>
 *   <li>rules: each {@code sdam:InferenceRule} whose condition matches adds its consequent.</li>
 * </ul>
 * Derived statements carry the source {@link Vocabulary#INFERENCE_SOURCE} and are recomputed on
 * every ingest, so they disappear when the fact they were derived from does.
 */
@Component(service = InferenceService.class)
public class InferenceService {
    private final OntologyRegistry ontology;

    @Activate
    public InferenceService(@Reference OntologyRegistry ontology) {
        this.ontology = ontology;
    }

    public AssetMetadata applyInferences(AssetMetadata metadata) {
        return applyInferences(metadata, ontology::findEntity);
    }

    public AssetMetadata applyInferences(AssetMetadata metadata, Function<String, Optional<Entity>> entities) {
        List<MetadataStatement> result = new ArrayList<>();
        for (MetadataStatement statement : metadata.statements()) {
            if (!Vocabulary.INFERENCE_SOURCE.equals(statement.source())) {
                result.add(statement);
            }
        }
        List<MetadataStatement> asserted = List.copyOf(result);
        for (MetadataStatement statement : asserted) {
            if (!statement.isValid()) {
                continue;
            }
            // Index at the specific concept; ancestors go under the predicate that expands it
            // (dcterms:subject -> sdam:broaderSubject), never under the asserted predicate.
            java.util.Collection<String> expansions = ontology.expansionsOf(statement.predicateUri());
            if (statement.objectIsUri() && !expansions.isEmpty()) {
                for (String broader : broaderClosure(statement.objectValue(), entities)) {
                    for (String expansion : expansions) {
                        addIfNew(result, MetadataStatement.uri(expansion, broader,
                                Vocabulary.INFERENCE_SOURCE, true, statement.confidenceScore()));
                    }
                }
            }
            String comparable = statement.objectIsUri()
                    ? entities.apply(statement.objectValue()).map(Entity::label).orElse(statement.objectValue())
                    : statement.objectValue();
            for (InferenceRule rule : ontology.rules()) {
                boolean matches = rule.ifObject() != null
                        ? statement.objectIsUri() && rule.ifObject().equals(statement.objectValue())
                        : rule.ifValuePattern().matcher(comparable).find();
                if (rule.ifPredicate().equals(statement.predicateUri()) && matches) {
                    addIfNew(result, rule.thenValueIsUri()
                            ? MetadataStatement.uri(rule.thenPredicate(), rule.thenValue(),
                                    Vocabulary.INFERENCE_SOURCE, true, statement.confidenceScore())
                            : MetadataStatement.literal(rule.thenPredicate(), rule.thenValue(),
                                    Vocabulary.INFERENCE_SOURCE, true, statement.confidenceScore()));
                }
            }
        }
        return new AssetMetadata(metadata.assetId(), result);
    }

    /** Ancestors from the ontology taxonomy and from the {@code broader} links of entities (AEM tags). */
    private java.util.Set<String> broaderClosure(String concept, Function<String, Optional<Entity>> entities) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>(List.of(concept));
        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<String> parents = new ArrayList<>(ontology.broaderConcepts(current));
            entities.apply(current).filter(Entity::approved).ifPresent(entity -> parents.addAll(entity.broader()));
            for (String parent : parents) {
                if (!parent.equals(concept) && seen.add(parent)) {
                    queue.add(parent);
                }
            }
        }
        return seen;
    }

    private static void addIfNew(List<MetadataStatement> statements, MetadataStatement candidate) {
        if (statements.stream().noneMatch(candidate::sameFact)) {
            statements.add(candidate);
        }
    }
}
