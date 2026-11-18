package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.OntologyRegistry;
import com.semanticdam.core.core.ontology.NodeShape;
import com.semanticdam.core.core.ontology.ShaclValidator;
import com.semanticdam.core.core.ontology.Violation;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Applies the ontology's SHACL shapes to asset statements. */
@Component(service = MetadataValidator.class)
public class MetadataValidator {
    private final OntologyRegistry ontology;

    @Activate
    public MetadataValidator(@Reference OntologyRegistry ontology) {
        this.ontology = ontology;
    }

    /** Validates one statement against the base asset shape, with ontology-declared entities only. */
    public boolean isValid(MetadataStatement statement) {
        return statementViolations(ontology.shapesFor(List.of(Vocabulary.ASSET)), statement, ontology::findEntity)
                .isEmpty();
    }

    /**
     * Returns the metadata with every statement's validity flag recomputed. Invalid statements are
     * kept, flagged, so that authors can see and fix them.
     */
    public AssetMetadata markValidity(AssetMetadata metadata, Function<String, Optional<Entity>> entities) {
        Collection<NodeShape> shapes = shapesFor(metadata.statements());
        List<MetadataStatement> marked = new ArrayList<>();
        for (MetadataStatement statement : metadata.statements()) {
            marked.add(statement.withValid(statementViolations(shapes, statement, entities).isEmpty()));
        }
        return new AssetMetadata(metadata.assetId(), marked);
    }

    /**
     * Every violation for a set of statements treated as one node, including cardinality. Pass the
     * conflict-resolved view: {@code sh:maxCount} is expected to fail on unresolved statements.
     */
    public List<Violation> violations(Collection<MetadataStatement> statements,
            Function<String, Optional<Entity>> entities) {
        Collection<NodeShape> shapes = shapesFor(statements);
        List<Violation> violations = new ArrayList<>(validator(entities).validateNode(shapes, statements));
        for (MetadataStatement statement : statements) {
            violations.addAll(entityViolations(statement, entities));
        }
        return violations;
    }

    public boolean conforms(NodeShape shape, Collection<MetadataStatement> statements,
            Function<String, Optional<Entity>> entities) {
        return validator(entities).conforms(shape, statements);
    }

    private List<Violation> statementViolations(Collection<NodeShape> shapes, MetadataStatement statement,
            Function<String, Optional<Entity>> entities) {
        List<Violation> violations = new ArrayList<>(validator(entities).validateStatement(shapes, statement));
        violations.addAll(entityViolations(statement, entities));
        return violations;
    }

    /**
     * An entity link is only as valid as the entity it points at: the entry must be approved in
     * its controlled vocabulary and its label must satisfy the entity's shapes.
     */
    private List<Violation> entityViolations(MetadataStatement statement,
            Function<String, Optional<Entity>> entities) {
        if (!statement.objectIsUri() || Vocabulary.RDF_TYPE.equals(statement.predicateUri())) {
            return List.of();
        }
        Optional<Entity> target = entities.apply(statement.objectValue());
        if (target.isEmpty()) {
            return List.of();
        }
        Entity entity = target.get();
        List<Violation> violations = new ArrayList<>();
        if (!entity.approved()) {
            String typeLabel = ontology.findByUri(entity.typeUri()).map(com.semanticdam.core.core.domain.OntologyTerm::label)
                    .orElse(entity.typeUri());
            violations.add(new Violation(VOCABULARY_SHAPE, statement.predicateUri(), statement.objectValue(),
                    "sdam:controlledVocabulary", switch (entity.status()) {
                        case CANDIDATE -> "'" + entity.label() + "' is not yet in the " + typeLabel
                                + " vocabulary; it awaits review by a vocabulary steward.";
                        case REJECTED -> "'" + entity.label() + "' was rejected from the " + typeLabel + " vocabulary.";
                        default -> "'" + entity.label() + "' was replaced by another " + typeLabel + " entry.";
                    }));
        }
        violations.addAll(validator(entities).validateNode(ontology.shapesFor(List.of(entity.typeUri())),
                List.of(MetadataStatement.literal(Vocabulary.PREF_LABEL, entity.label()))));
        return violations;
    }

    private static final String VOCABULARY_SHAPE = Vocabulary.SDAM + "ControlledVocabulary";

    private Collection<NodeShape> shapesFor(Collection<MetadataStatement> statements) {
        Set<String> classes = new LinkedHashSet<>();
        classes.add(Vocabulary.ASSET);
        for (MetadataStatement statement : statements) {
            if (statement.objectIsUri() && Vocabulary.RDF_TYPE.equals(statement.predicateUri())) {
                classes.add(statement.objectValue());
            }
        }
        return ontology.shapesFor(classes);
    }

    private ShaclValidator validator(Function<String, Optional<Entity>> entities) {
        return new ShaclValidator(entities, ontology::superClasses);
    }
}
