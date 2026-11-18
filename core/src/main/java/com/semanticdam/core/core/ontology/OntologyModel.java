package com.semanticdam.core.core.ontology;

import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.OntologyTerm;
import com.semanticdam.core.core.graph.OntologyRegistry;
import com.semanticdam.core.core.rdf.RdfGraph;
import com.semanticdam.core.core.rdf.RdfTerm;
import com.semanticdam.core.core.rdf.RdfTriple;
import com.semanticdam.core.core.rdf.TurtleParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The ontology as loaded from one or more Turtle documents. Immutable once built, and fails fast:
 * a malformed document, mapping, shape or rule throws at construction instead of being skipped.
 */
public final class OntologyModel implements OntologyRegistry {
    private static final String RDFS_LABEL = Vocabulary.RDFS + "label";
    private static final String RDFS_COMMENT = Vocabulary.RDFS + "comment";
    private static final String RDFS_RANGE = Vocabulary.RDFS + "range";
    private static final String RDFS_SUBCLASS_OF = Vocabulary.RDFS + "subClassOf";
    private static final String ALT_LABEL = Vocabulary.SKOS + "altLabel";
    private static final String HIDDEN_LABEL = Vocabulary.SKOS + "hiddenLabel";
    private static final String DEFINITION = Vocabulary.SKOS + "definition";
    private static final String IN_SCHEME = Vocabulary.SKOS + "inScheme";
    private static final String BROADER = Vocabulary.SKOS + "broader";
    private static final String SH = Vocabulary.SH;
    private static final String SDAM = Vocabulary.SDAM;

    private final RdfGraph graph;
    private final String version;
    private final Map<String, OntologyTerm> byUri = new LinkedHashMap<>();
    private final Map<String, OntologyTerm> byLabel = new HashMap<>();
    private final Map<String, SourceSystem> sourceSystems = new LinkedHashMap<>();
    private final List<PropertyMapping> mappings = new ArrayList<>();
    private final List<NodeShape> shapes = new ArrayList<>();
    private final List<InferenceRule> rules = new ArrayList<>();
    private final Map<String, Entity> entities = new LinkedHashMap<>();
    private final Map<String, Entity> entitiesByLabel = new HashMap<>();
    private final Map<String, String> instanceNamespaces = new HashMap<>();
    private final Map<String, List<String>> expansions = new HashMap<>();

    public OntologyModel(List<String> turtleDocuments) {
        if (turtleDocuments == null || turtleDocuments.isEmpty()) {
            throw new IllegalArgumentException("at least one ontology document is required");
        }
        List<RdfTriple> triples = new ArrayList<>();
        for (int i = 0; i < turtleDocuments.size(); i++) {
            triples.addAll(TurtleParser.parse(turtleDocuments.get(i), "d" + i + "b").triples());
        }
        this.graph = new RdfGraph(triples);
        this.version = readVersion();
        readInstanceNamespaces();
        readShapes();
        readTerms();
        readSourceSystems();
        readMappings();
        readRules();
        readEntities();
        for (RdfTriple triple : graph.withPredicate(SDAM + "expands")) {
            expansions.computeIfAbsent(triple.object().value(), key -> new ArrayList<>()).add(triple.subject().value());
        }
    }

    public static OntologyModel fromTurtle(String... turtleDocuments) {
        return new OntologyModel(List.of(turtleDocuments));
    }

    public RdfGraph graph() {
        return graph;
    }

    @Override
    public Optional<OntologyTerm> findByUri(String uri) {
        return uri == null || uri.isBlank() ? Optional.empty() : Optional.ofNullable(byUri.get(uri));
    }

    @Override
    public Optional<OntologyTerm> findByLabel(String label) {
        return label == null || label.isBlank() ? Optional.empty()
                : Optional.ofNullable(byLabel.get(normalize(label)));
    }

    @Override
    public boolean isAllowedForBundle(String predicateUri, String bundleName) {
        Optional<OntologyTerm> bundle = findByLabel(bundleName)
                .filter(term -> term.kind() == OntologyTerm.Kind.CLASS);
        if (bundle.isEmpty()) {
            return true;
        }
        return shapesFor(List.of(bundle.get().uri())).stream()
                .filter(NodeShape::closed)
                .allMatch(shape -> shape.declares(predicateUri) || shape.ignoredProperties().contains(predicateUri));
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public Collection<OntologyTerm> terms() {
        return List.copyOf(byUri.values());
    }

    @Override
    public Collection<SourceSystem> sourceSystems() {
        return List.copyOf(sourceSystems.values());
    }

    @Override
    public Collection<PropertyMapping> mappings() {
        return List.copyOf(mappings);
    }

    @Override
    public Collection<InferenceRule> rules() {
        return List.copyOf(rules);
    }

    @Override
    public Collection<NodeShape> shapesFor(Collection<String> classUris) {
        Set<String> classes = new HashSet<>();
        for (String classUri : classUris) {
            classes.addAll(superClasses(classUri));
        }
        return shapes.stream()
                .filter(shape -> !shape.readiness())
                .filter(shape -> shape.target().map(classes::contains).orElse(false))
                .toList();
    }

    @Override
    public Collection<NodeShape> readinessShapes() {
        return shapes.stream().filter(NodeShape::readiness).toList();
    }

    @Override
    public Set<String> superClasses(String classUri) {
        Set<String> result = new LinkedHashSet<>();
        result.add(classUri);
        result.addAll(closure(classUri, RDFS_SUBCLASS_OF));
        return result;
    }

    @Override
    public Set<String> broaderConcepts(String conceptUri) {
        return closure(conceptUri, BROADER);
    }

    @Override
    public Optional<Entity> findEntity(String uri) {
        return Optional.ofNullable(entities.get(uri));
    }

    @Override
    public Optional<Entity> findEntityByLabel(String classUri, String label) {
        if (classUri == null || label == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entitiesByLabel.get(classUri + "|" + normalize(label)));
    }

    @Override
    public Optional<String> instanceNamespace(String classUri) {
        return Optional.ofNullable(instanceNamespaces.get(classUri));
    }

    @Override
    public Collection<String> expansionsOf(String predicateUri) {
        return List.copyOf(expansions.getOrDefault(predicateUri, List.of()));
    }

    /** {@code skos:definition} of a term, if declared. */
    public Optional<String> definition(String uri) {
        return graph.string(RdfTerm.iri(uri), DEFINITION);
    }

    private Set<String> closure(String start, String predicate) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>(List.of(start));
        while (!queue.isEmpty()) {
            for (RdfTerm parent : graph.objects(RdfTerm.iri(queue.remove(0)), predicate)) {
                if (parent.isIri() && !parent.value().equals(start) && seen.add(parent.value())) {
                    queue.add(parent.value());
                }
            }
        }
        return seen;
    }

    private String readVersion() {
        List<String> versions = new ArrayList<>();
        for (RdfTerm ontology : graph.instancesOf(Vocabulary.OWL + "Ontology")) {
            graph.string(ontology, Vocabulary.OWL + "versionInfo").ifPresent(versions::add);
        }
        return versions.isEmpty() ? "unversioned" : String.join("+", versions);
    }

    private void readInstanceNamespaces() {
        for (RdfTriple triple : graph.withPredicate(SDAM + "instanceNamespace")) {
            if (graph.instancesOf(SDAM + "PropertyMapping").contains(triple.subject())) {
                continue;
            }
            instanceNamespaces.put(triple.subject().value(), triple.object().value());
        }
    }

    private void readTerms() {
        Map<String, String> assetPatterns = new HashMap<>();
        for (NodeShape shape : shapes) {
            if (Vocabulary.ASSET.equals(shape.targetClass())) {
                for (PropertyShape property : shape.properties()) {
                    if (property.pattern() != null) {
                        assetPatterns.put(property.path(), property.pattern());
                    }
                }
            }
        }
        Map<String, OntologyTerm.Kind> kinds = new LinkedHashMap<>();
        kinds.put(Vocabulary.OWL + "Class", OntologyTerm.Kind.CLASS);
        kinds.put(Vocabulary.OWL + "DatatypeProperty", OntologyTerm.Kind.DATATYPE_PROPERTY);
        kinds.put(Vocabulary.OWL + "ObjectProperty", OntologyTerm.Kind.OBJECT_PROPERTY);
        kinds.put(Vocabulary.CONCEPT_CLASS, OntologyTerm.Kind.CONCEPT);
        kinds.put(Vocabulary.CONCEPT_SCHEME_CLASS, OntologyTerm.Kind.OTHER);
        for (Map.Entry<String, OntologyTerm.Kind> kind : kinds.entrySet()) {
            for (RdfTerm subject : graph.instancesOf(kind.getKey())) {
                if (!subject.isIri() || byUri.containsKey(subject.value())) {
                    continue;
                }
                String uri = subject.value();
                String label = graph.string(subject, RDFS_LABEL)
                        .or(() -> graph.string(subject, Vocabulary.PREF_LABEL))
                        .orElse(localName(uri));
                OntologyTerm term = new OntologyTerm(uri, label, assetPatterns.get(uri), kind.getValue(),
                        graph.string(subject, DEFINITION).or(() -> graph.string(subject, RDFS_COMMENT)).orElse(null),
                        graph.string(subject, RDFS_RANGE).orElse(null));
                byUri.put(uri, term);
                byLabel.putIfAbsent(normalize(label), term);
                byLabel.putIfAbsent(normalize(localName(uri)), term);
            }
        }
    }

    private void readSourceSystems() {
        for (RdfTerm subject : graph.instancesOf(SDAM + "SourceSystem")) {
            String label = graph.string(subject, RDFS_LABEL).orElse(Vocabulary.sourceLabel(subject.value()));
            double trust = graph.string(subject, SDAM + "trust").map(Double::parseDouble).orElse(0.5);
            sourceSystems.put(subject.value(), new SourceSystem(subject.value(), label, trust));
        }
    }

    private void readMappings() {
        for (RdfTerm subject : graph.instancesOf(SDAM + "PropertyMapping")) {
            String system = required(subject, SDAM + "sourceSystem");
            SourceSystem sourceSystem = sourceSystems.get(system);
            if (sourceSystem == null) {
                throw new IllegalStateException("mapping " + subject + " names unknown source system " + system);
            }
            mappings.add(new PropertyMapping(required(subject, SDAM + "sourceProperty"), sourceSystem,
                    required(subject, SDAM + "predicate"), graph.string(subject, SDAM + "entityClass").orElse(null),
                    graph.string(subject, SDAM + "valueKind").orElse(null),
                    graph.string(subject, SDAM + "instanceNamespace").orElse(null),
                    PropertyMapping.APPROVED_POLICY.equals(graph.string(subject, SDAM + "entityPolicy").orElse(null))));
        }
    }

    private void readRules() {
        for (RdfTerm subject : graph.instancesOf(SDAM + "InferenceRule")) {
            RdfTerm then = graph.object(subject, SDAM + "thenValue")
                    .orElseThrow(() -> new IllegalStateException("rule " + subject + " has no sdam:thenValue"));
            rules.add(new InferenceRule(subject.value(),
                    graph.string(subject, RDFS_LABEL).orElse(localName(subject.value())),
                    required(subject, SDAM + "ifPredicate"),
                    graph.string(subject, SDAM + "ifObject").orElse(null),
                    graph.string(subject, SDAM + "ifValuePattern").map(Pattern::compile).orElse(null),
                    required(subject, SDAM + "thenPredicate"), then.value(), then.isIri()));
        }
    }

    private void readShapes() {
        for (RdfTerm subject : graph.instancesOf(SH + "NodeShape")) {
            List<PropertyShape> properties = new ArrayList<>();
            for (RdfTerm property : graph.objects(subject, SH + "property")) {
                RdfTerm path = graph.object(property, SH + "path").orElse(null);
                if (path == null || !path.isIri()) {
                    throw new IllegalStateException("shape " + subject + " has a property without an IRI sh:path");
                }
                List<String> in = graph.object(property, SH + "in")
                        .map(head -> graph.list(head).stream().map(RdfTerm::value).toList()).orElse(null);
                properties.add(new PropertyShape(path.value(),
                        graph.string(property, SH + "datatype").orElse(null),
                        graph.string(property, SH + "pattern").orElse(null),
                        graph.string(property, SH + "flags").orElse(null),
                        integer(property, SH + "minLength"), integer(property, SH + "maxLength"),
                        integer(property, SH + "minCount"), integer(property, SH + "maxCount"),
                        graph.string(property, SH + "nodeKind").orElse(null),
                        graph.string(property, SH + "class").orElse(null), in,
                        graph.string(property, SH + "message").orElse(null)));
            }
            Set<String> ignored = new HashSet<>();
            graph.object(subject, SH + "ignoredProperties")
                    .ifPresent(head -> graph.list(head).forEach(term -> ignored.add(term.value())));
            shapes.add(new NodeShape(subject.value(),
                    graph.string(subject, RDFS_LABEL).orElse(localName(subject.value())),
                    graph.string(subject, SH + "targetClass").orElse(null),
                    graph.string(subject, SH + "closed").map(Boolean::parseBoolean).orElse(false),
                    ignored, properties,
                    graph.instancesOf(SDAM + "ReadinessShape").contains(subject)));
        }
    }

    private void readEntities() {
        List<String> classes = new ArrayList<>(instanceNamespaces.keySet());
        classes.add(Vocabulary.CONCEPT_SCHEME_CLASS);
        for (String classUri : classes) {
            for (RdfTerm subject : graph.instancesOf(classUri)) {
                if (!subject.isIri() || entities.containsKey(subject.value())) {
                    continue;
                }
                String label = graph.string(subject, Vocabulary.PREF_LABEL)
                        .or(() -> graph.string(subject, RDFS_LABEL)).orElse(localName(subject.value()));
                Entity entity = new Entity(subject.value(), classUri, label,
                        values(subject, BROADER), values(subject, ALT_LABEL), values(subject, HIDDEN_LABEL),
                        graph.string(subject, IN_SCHEME).orElse(null), Entity.Status.APPROVED, null);
                entities.put(entity.uri(), entity);
                entitiesByLabel.putIfAbsent(classUri + "|" + normalize(label), entity);
                for (String alternative : entity.altLabels()) {
                    entitiesByLabel.putIfAbsent(classUri + "|" + normalize(alternative), entity);
                }
                for (String hidden : entity.hiddenLabels()) {
                    entitiesByLabel.putIfAbsent(classUri + "|" + normalize(hidden), entity);
                }
            }
        }
    }

    private List<String> values(RdfTerm subject, String predicate) {
        return graph.objects(subject, predicate).stream().map(RdfTerm::value).toList();
    }

    private String required(RdfTerm subject, String predicate) {
        return graph.string(subject, predicate).orElseThrow(
                () -> new IllegalStateException(subject + " is missing required property <" + predicate + ">"));
    }

    private Integer integer(RdfTerm subject, String predicate) {
        return graph.string(subject, predicate).map(Integer::valueOf).orElse(null);
    }

    private static String localName(String uri) {
        int cut = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/'));
        return cut >= 0 && cut + 1 < uri.length() ? uri.substring(cut + 1) : uri;
    }

    private static String normalize(String label) {
        return label.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
