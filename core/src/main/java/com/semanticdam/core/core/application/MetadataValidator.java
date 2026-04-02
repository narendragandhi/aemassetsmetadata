package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.domain.OntologyTerm;
import com.semanticdam.core.core.graph.OntologyRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Pattern;

@Component(service = MetadataValidator.class)
public class MetadataValidator {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataValidator.class);
    private final OntologyRegistry ontologyRegistry;

    @Activate
    public MetadataValidator(@Reference OntologyRegistry ontologyRegistry) {
        this.ontologyRegistry = ontologyRegistry;
    }

    public boolean isValid(MetadataStatement statement) {
        Optional<OntologyTerm> term = ontologyRegistry.findByUri(statement.predicateUri());
        if (term.isEmpty()) {
            // If predicate is not in ontology, we allow it (soft validation)
            return true;
        }

        Optional<String> patternStr = term.get().validationPattern();
        if (patternStr.isEmpty()) {
            return true;
        }

        try {
            boolean matches = Pattern.matches(patternStr.get(), statement.objectValue());
            if (!matches) {
                LOG.warn("Metadata validation failed for {}: '{}' does not match pattern '{}'",
                        statement.predicateUri(), statement.objectValue(), patternStr.get());
            }
            return matches;
        } catch (Exception e) {
            LOG.error("Error validating metadata statement {}", statement, e);
            return false;
        }
    }
}
