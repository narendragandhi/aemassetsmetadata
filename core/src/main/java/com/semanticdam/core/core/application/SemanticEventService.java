package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.OntologyRegistry;
import com.semanticdam.core.core.ontology.NodeShape;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Posts an OSGi event when an asset starts to conform to a readiness shape of the ontology
 * (for example {@code sdam:AjoReadyShape}). The event fires on the transition only, not on every
 * ingest of an asset that is already ready.
 *
 * <p>Event properties: {@code path}, {@code consumer} (the shape's label) and {@code shape}.
 * Forwarding to Adobe I/O Events is left to a handler of this topic.</p>
 */
@Component(service = SemanticEventService.class)
public class SemanticEventService {
    public static final String TOPIC_READY = "com/semanticdam/asset/READY";
    private static final Logger LOG = LoggerFactory.getLogger(SemanticEventService.class);

    private final OntologyRegistry ontology;
    private final MetadataValidator validator;
    private final EventAdmin eventAdmin;

    @Activate
    public SemanticEventService(@Reference OntologyRegistry ontology, @Reference MetadataValidator validator,
            @Reference EventAdmin eventAdmin) {
        this.ontology = ontology;
        this.validator = validator;
        this.eventAdmin = eventAdmin;
    }

    /** @return the labels of the consumers the asset has just become ready for */
    public List<String> evaluateAndEmit(Optional<AssetMetadata> previous, AssetMetadata current,
            Function<String, Optional<Entity>> entities) {
        List<String> emitted = new ArrayList<>();
        for (NodeShape shape : ontology.readinessShapes()) {
            boolean wasReady = previous.map(metadata -> ready(shape, metadata, entities)).orElse(false);
            if (!wasReady && ready(shape, current, entities)) {
                LOG.info("Asset {} is ready for {}", current.assetId().path(), shape.label());
                eventAdmin.postEvent(new Event(TOPIC_READY, Map.of(
                        "path", current.assetId().path(), "consumer", shape.label(), "shape", shape.uri())));
                emitted.add(shape.label());
            }
        }
        return emitted;
    }

    private boolean ready(NodeShape shape, AssetMetadata metadata, Function<String, Optional<Entity>> entities) {
        List<MetadataStatement> valid = metadata.statements().stream().filter(MetadataStatement::isValid).toList();
        return validator.conforms(shape, valid, entities);
    }
}
