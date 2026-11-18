package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import com.semanticdam.core.core.graph.OntologyRegistry;
import com.semanticdam.core.core.ontology.SourceSystem;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * DAM properties -> mapped statements -> merge -> inference -> validation -> graph -> events.
 *
 * <p>The pipeline owns the statements whose source is one of the ontology's source systems or the
 * inference engine: those are rebuilt on every ingest. Statements from any other source, such as
 * reviewed AI facts, were not derived from DAM properties and are carried over untouched.</p>
 */
@Component(service = AssetMetadataPipeline.class)
public class DefaultAssetMetadataPipeline implements AssetMetadataPipeline {
    private final AemMetadataSource metadataSource;
    private final AssetMetadataExtractionService extractionService;
    private final AssetMetadataIndexingService indexingService;
    private final SemanticEventService eventService;
    private final InferenceService inferenceService;
    private final MetadataValidator validator;
    private final OntologyRegistry ontology;
    private final KnowledgeGraphService graph;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    private volatile MetadataWriteBack writeBack;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    private volatile ConflictResolutionService conflicts;

    void setWriteBack(MetadataWriteBack writeBack, ConflictResolutionService conflicts) {
        this.writeBack = writeBack;
        this.conflicts = conflicts;
    }

    @Activate
    public DefaultAssetMetadataPipeline(
            @Reference AemMetadataSource metadataSource,
            @Reference AssetMetadataExtractionService extractionService,
            @Reference AssetMetadataIndexingService indexingService,
            @Reference SemanticEventService eventService,
            @Reference InferenceService inferenceService,
            @Reference MetadataValidator validator,
            @Reference OntologyRegistry ontology,
            @Reference KnowledgeGraphService graph) {
        this.metadataSource = metadataSource;
        this.extractionService = extractionService;
        this.indexingService = indexingService;
        this.eventService = eventService;
        this.inferenceService = inferenceService;
        this.validator = validator;
        this.ontology = ontology;
        this.graph = graph;
    }

    @Override
    public Optional<AssetMetadata> ingest(AssetId assetId) {
        Optional<Map<String, Object>> properties = metadataSource.readMetadata(assetId);
        if (properties.isEmpty()) {
            remove(assetId);
            return Optional.empty();
        }
        ExtractedAsset extracted = extractionService.extract(assetId, properties.get(), graph::findEntity);
        Optional<AssetMetadata> previous = graph.findByAssetId(assetId);

        List<MetadataStatement> statements = new ArrayList<>(extracted.metadata().statements());
        Set<String> managed = managedSources();
        previous.ifPresent(stored -> stored.statements().stream()
                .filter(statement -> !managed.contains(statement.source()))
                .forEach(statements::add));

        Map<String, Optional<Entity>> cache = new HashMap<>();
        Function<String, Optional<Entity>> entities = uri -> cache.computeIfAbsent(uri, key ->
                Optional.ofNullable(extracted.entities().get(key))
                        .or(() -> ontology.findEntity(key))
                        .or(() -> graph.findEntity(key)));

        AssetMetadata metadata = validator.markValidity(new AssetMetadata(assetId, statements), entities);
        metadata = inferenceService.applyInferences(metadata, entities);
        metadata = validator.markValidity(metadata, entities);

        List<Entity> linked = new ArrayList<>();
        for (MetadataStatement statement : metadata.statements()) {
            if (statement.objectIsUri() && !Vocabulary.RDF_TYPE.equals(statement.predicateUri())) {
                entities.apply(statement.objectValue()).ifPresent(linked::add);
            }
        }
        indexingService.indexEntities(linked);
        indexingService.index(metadata);
        eventService.evaluateAndEmit(previous, metadata, entities);
        MetadataWriteBack target = writeBack;
        ConflictResolutionService resolver = conflicts;
        if (target != null && resolver != null) {
            java.util.Collection<MetadataStatement> authoritative = resolver.resolve(metadata);
            target.write(assetId, FactWriteBackService.properties(authoritative, entities,
                    !validator.violations(authoritative, entities).isEmpty()), ontology.version());
        }
        return Optional.of(metadata);
    }

    @Override
    public void remove(AssetId assetId) {
        indexingService.remove(assetId);
    }

    private Set<String> managedSources() {
        Set<String> managed = new HashSet<>();
        managed.add(Vocabulary.INFERENCE_SOURCE);
        for (SourceSystem system : ontology.sourceSystems()) {
            managed.add(system.label());
        }
        return managed;
    }
}
