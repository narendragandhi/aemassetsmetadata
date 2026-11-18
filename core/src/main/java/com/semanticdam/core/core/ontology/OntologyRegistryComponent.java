package com.semanticdam.core.core.ontology;

import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.OntologyTerm;
import com.semanticdam.core.core.graph.ClasspathOntologySource;
import com.semanticdam.core.core.graph.JcrKnowledgeGraphStore;
import com.semanticdam.core.core.graph.OntologyRegistry;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Publishes the ontology as an OSGi service. The bundled core ontology is always loaded;
 * project-specific Turtle files (extra classes, mappings, shapes, taxonomy, rules) can be added
 * from the repository without rebuilding the bundle.
 */
@Component(service = OntologyRegistry.class)
@Designate(ocd = OntologyRegistryComponent.Config.class)
public class OntologyRegistryComponent implements OntologyRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(OntologyRegistryComponent.class);
    static final String CORE_ONTOLOGY = "/ontology/asset-ontology.ttl";
    static final String DEFAULT_PROFILE = "/ontology/application-profile.ttl";

    @ObjectClassDefinition(name = "SemanticDAM - Ontology")
    public @interface Config {
        @AttributeDefinition(name = "Extension files",
                description = "Repository paths of Turtle files (nt:file) merged into the bundled ontology, "
                        + "for example /conf/semanticdam/settings/ontology/retail.ttl. Readable by the "
                        + "SemanticDAM service user. A file that fails to parse is rejected as a whole and "
                        + "the previous ontology stays active.")
        String[] extensionPaths() default {};
    }

    private final ResourceResolverFactory resolverFactory;
    private volatile OntologyModel model;

    @Activate
    public OntologyRegistryComponent(@Reference ResourceResolverFactory resolverFactory, Config config) {
        this.resolverFactory = resolverFactory;
        this.model = OntologyModel.fromTurtle(new ClasspathOntologySource(CORE_ONTOLOGY).readTurtle(),
                new ClasspathOntologySource(DEFAULT_PROFILE).readTurtle());
        load(config);
    }

    @Modified
    protected void modified(Config config) {
        load(config);
    }

    private void load(Config config) {
        String[] paths = config.extensionPaths() == null ? new String[0] : config.extensionPaths();
        List<String> documents = new ArrayList<>();
        documents.add(new ClasspathOntologySource(CORE_ONTOLOGY).readTurtle());
        documents.add(new ClasspathOntologySource(DEFAULT_PROFILE).readTurtle());
        if (paths.length > 0) {
            try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(
                    Map.of(ResourceResolverFactory.SUBSERVICE, JcrKnowledgeGraphStore.SERVICE_USER))) {
                for (String path : paths) {
                    Resource resource = path == null || path.isBlank() ? null : resolver.getResource(path);
                    InputStream stream = resource == null ? null : resource.adaptTo(InputStream.class);
                    if (stream == null) {
                        LOG.error("Ontology extension {} was not found or is not readable; keeping the "
                                + "current ontology {}", path, model.version());
                        return;
                    }
                    try (stream) {
                        documents.add(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            } catch (LoginException | IOException e) {
                LOG.error("Could not read ontology extensions; keeping the current ontology {}", model.version(), e);
                return;
            }
        }
        try {
            this.model = new OntologyModel(documents);
            LOG.info("Loaded ontology {}: {} terms, {} mappings, {} rules", model.version(),
                    model.terms().size(), model.mappings().size(), model.rules().size());
        } catch (RuntimeException e) {
            LOG.error("Ontology extensions are invalid; keeping the current ontology {}", model.version(), e);
        }
    }

    @Override public Optional<OntologyTerm> findByUri(String uri) { return model.findByUri(uri); }
    @Override public Optional<OntologyTerm> findByLabel(String label) { return model.findByLabel(label); }
    @Override public boolean isAllowedForBundle(String predicateUri, String bundleName) {
        return model.isAllowedForBundle(predicateUri, bundleName);
    }
    @Override public String version() { return model.version(); }
    @Override public Collection<OntologyTerm> terms() { return model.terms(); }
    @Override public Collection<SourceSystem> sourceSystems() { return model.sourceSystems(); }
    @Override public Collection<PropertyMapping> mappings() { return model.mappings(); }
    @Override public Collection<InferenceRule> rules() { return model.rules(); }
    @Override public Collection<NodeShape> shapesFor(Collection<String> classUris) { return model.shapesFor(classUris); }
    @Override public Collection<NodeShape> readinessShapes() { return model.readinessShapes(); }
    @Override public Set<String> superClasses(String classUri) { return model.superClasses(classUri); }
    @Override public Set<String> broaderConcepts(String conceptUri) { return model.broaderConcepts(conceptUri); }
    @Override public Optional<Entity> findEntity(String uri) { return model.findEntity(uri); }
    @Override public Optional<Entity> findEntityByLabel(String classUri, String label) {
        return model.findEntityByLabel(classUri, label);
    }
    @Override public Optional<String> instanceNamespace(String classUri) { return model.instanceNamespace(classUri); }
    @Override public Collection<String> expansionsOf(String predicateUri) { return model.expansionsOf(predicateUri); }
}
