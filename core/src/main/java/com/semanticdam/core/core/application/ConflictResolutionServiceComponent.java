package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.OntologyRegistry;
import com.semanticdam.core.core.ontology.NodeShape;
import com.semanticdam.core.core.ontology.PropertyShape;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Produces the authoritative view of an asset's statements.
 *
 * <p>A predicate is single-valued when it has a precedence rule here or the asset shape gives it
 * {@code sh:maxCount 1}. For those, one valid value wins: with a precedence rule, the value from the
 * first listed source; without one, the value from the most trusted source. Every other predicate is multi-valued and
 * keeps all of its distinct values (an asset can have several subjects, some of them inferred).</p>
 */
@Component(service = ConflictResolutionService.class)
@Designate(ocd = ConflictResolutionServiceComponent.Config.class)
public class ConflictResolutionServiceComponent implements ConflictResolutionService {

    @ObjectClassDefinition(name = "SemanticDAM - Conflict Resolution")
    public @interface Config {
        @AttributeDefinition(
            name = "Precedence Rules",
            description = "Sources in order of authority per predicate. Format: predicateUri=Source1,Source2"
        )
        String[] precedence_rules() default {
            Vocabulary.SKU + "=PIM,AEM",
            Vocabulary.DEPICTS_PRODUCT + "=PIM,AEM",
            Vocabulary.TITLE + "=AEM,Workfront",
            Vocabulary.PART_OF_CAMPAIGN + "=Workfront,AEM"
        };

        @AttributeDefinition(
            name = "Default Source Priority",
            description = "Tie-breaker between equally trusted sources of a single-valued predicate "
                    + "without a precedence rule."
        )
        String[] default_priority() default {"PIM", "Workfront", "AEM"};
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private volatile OntologyRegistry ontology;

    private volatile Map<String, List<String>> precedenceMap = Map.of();
    private volatile List<String> defaultPriority = List.of();

    @Activate
    @Modified
    protected void activate(Config config) {
        Map<String, List<String>> rules = new HashMap<>();
        for (String rule : config.precedence_rules()) {
            int separator = rule.lastIndexOf('=');
            if (separator > 0 && separator < rule.length() - 1) {
                rules.put(rule.substring(0, separator).trim(), Arrays.stream(rule.substring(separator + 1).split(","))
                        .map(String::trim).filter(source -> !source.isEmpty()).collect(Collectors.toList()));
            }
        }
        precedenceMap = Map.copyOf(rules);
        defaultPriority = Arrays.stream(config.default_priority()).map(String::trim).collect(Collectors.toList());
    }

    void setOntology(OntologyRegistry ontology) {
        this.ontology = ontology;
    }

    @Override
    public Collection<MetadataStatement> resolve(AssetMetadata metadata) {
        Map<String, List<MetadataStatement>> grouped = new LinkedHashMap<>();
        for (MetadataStatement statement : metadata.statements()) {
            grouped.computeIfAbsent(statement.predicateUri(), key -> new ArrayList<>()).add(statement);
        }
        List<MetadataStatement> resolved = new ArrayList<>();
        for (Map.Entry<String, List<MetadataStatement>> entry : grouped.entrySet()) {
            List<MetadataStatement> options = entry.getValue();
            if (isSingleValued(entry.getKey())) {
                resolved.add(winner(entry.getKey(), options));
            } else {
                resolved.addAll(distinctFacts(options));
            }
        }
        return resolved;
    }

    private boolean isSingleValued(String predicateUri) {
        if (precedenceMap.containsKey(predicateUri)) {
            return true;
        }
        OntologyRegistry registry = ontology;
        if (registry == null) {
            return false;
        }
        for (NodeShape shape : registry.shapesFor(List.of(Vocabulary.ASSET))) {
            for (PropertyShape property : shape.properties()) {
                if (property.path().equals(predicateUri) && property.maxCount() != null && property.maxCount() == 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private MetadataStatement winner(String predicateUri, List<MetadataStatement> options) {
        List<MetadataStatement> valid = options.stream().filter(MetadataStatement::isValid).toList();
        List<MetadataStatement> candidates = valid.isEmpty() ? options : valid;
        List<String> rule = precedenceMap.get(predicateUri);
        Comparator<MetadataStatement> byTrust =
                Comparator.comparingDouble(MetadataStatement::confidenceScore).reversed();
        // An explicit rule is a business decision and outranks trust; without one, trust decides
        // and the default priority only breaks ties.
        Comparator<MetadataStatement> order = rule != null
                ? byPosition(rule).thenComparing(byTrust)
                : byTrust.thenComparing(byPosition(defaultPriority));
        return candidates.stream().min(order).orElseThrow();
    }

    private static Comparator<MetadataStatement> byPosition(List<String> priority) {
        return Comparator.comparingInt(option -> {
            int index = priority.indexOf(option.source());
            return index < 0 ? Integer.MAX_VALUE : index;
        });
    }

    /** Collapses the same fact asserted by several sources into its most trusted assertion. */
    private List<MetadataStatement> distinctFacts(List<MetadataStatement> options) {
        List<MetadataStatement> distinct = new ArrayList<>();
        for (MetadataStatement option : options) {
            int existing = -1;
            for (int i = 0; i < distinct.size(); i++) {
                if (distinct.get(i).sameFact(option)) {
                    existing = i;
                }
            }
            if (existing < 0) {
                distinct.add(option);
            } else if (option.confidenceScore() > distinct.get(existing).confidenceScore()) {
                distinct.set(existing, option);
            }
        }
        return distinct;
    }
}
