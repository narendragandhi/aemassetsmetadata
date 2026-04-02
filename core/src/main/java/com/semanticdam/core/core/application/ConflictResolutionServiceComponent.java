package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Component(service = ConflictResolutionService.class)
@Designate(ocd = ConflictResolutionServiceComponent.Config.class)
public class ConflictResolutionServiceComponent implements ConflictResolutionService {

    private static final Logger LOG = LoggerFactory.getLogger(ConflictResolutionServiceComponent.class);

    @ObjectClassDefinition(name = "Asset Metadata Conflict Resolution Configuration")
    public @interface Config {
        @AttributeDefinition(
            name = "Precedence Rules",
            description = "Map predicates to a prioritized list of sources (comma-separated). Format: predicateUri=Source1,Source2"
        )
        String[] precedence_rules() default {
            "https://example.com/aem-assets-ontology#sku=PIM,AEM",
            "http://purl.org/dc/terms/title=AEM,Workfront",
            "https://example.com/aem-assets-ontology#belongsToCampaign=Workfront,AEM"
        };

        @AttributeDefinition(
            name = "Default Source Priority",
            description = "Default source priority for any predicate not explicitly mapped."
        )
        String[] default_priority() default {"AEM", "PIM", "Workfront"};
    }

    private Map<String, List<String>> precedenceMap = new HashMap<>();
    private List<String> defaultPriority = new ArrayList<>();

    @Activate
    @Modified
    protected void activate(Config config) {
        precedenceMap.clear();
        for (String rule : config.precedence_rules()) {
            String[] parts = rule.split("=");
            if (parts.length == 2) {
                String predicate = parts[0].trim();
                List<String> sources = Arrays.stream(parts[1].split(","))
                        .map(String::trim)
                        .collect(Collectors.toList());
                precedenceMap.put(predicate, sources);
            }
        }
        defaultPriority = Arrays.stream(config.default_priority())
                .map(String::trim)
                .collect(Collectors.toList());
        LOG.info("ConflictResolutionService activated with {} rules", precedenceMap.size());
    }

    @Override
    public Collection<MetadataStatement> resolve(AssetMetadata metadata) {
        Map<String, MetadataStatement> resolved = new HashMap<>();

        // Group statements by predicate
        Map<String, List<MetadataStatement>> grouped = metadata.statements().stream()
                .collect(Collectors.groupingBy(MetadataStatement::predicateUri));

        for (Map.Entry<String, List<MetadataStatement>> entry : grouped.entrySet()) {
            String predicate = entry.getKey();
            List<MetadataStatement> options = entry.getValue();

            if (options.size() == 1) {
                resolved.put(predicate, options.get(0));
                continue;
            }

            // More than one source for the same predicate - Apply Precedence
            List<String> priority = precedenceMap.getOrDefault(predicate, defaultPriority);
            
            MetadataStatement winner = findWinner(options, priority);
            resolved.put(predicate, winner);
        }

        return resolved.values();
    }

    private MetadataStatement findWinner(List<MetadataStatement> options, List<String> priority) {
        // Find the statement whose source has the highest priority (lowest index in the priority list)
        MetadataStatement winner = null;
        int bestIndex = Integer.MAX_VALUE;

        for (MetadataStatement option : options) {
            int index = priority.indexOf(option.source());
            if (index != -1 && index < bestIndex) {
                bestIndex = index;
                winner = option;
            }
        }

        // If no source matches the priority list, just pick the first one (fallback)
        return winner != null ? winner : options.get(0);
    }
}
