package com.semanticdam.core.core.ontology;

import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.rdf.RdfTerm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates statements about one node against SHACL Core node shapes.
 *
 * <p>Supported constraints: {@code sh:datatype}, {@code sh:pattern}/{@code sh:flags},
 * {@code sh:minLength}, {@code sh:maxLength}, {@code sh:nodeKind}, {@code sh:class}, {@code sh:in},
 * {@code sh:minCount}, {@code sh:maxCount} and {@code sh:closed}. Statements carry untyped strings,
 * so {@code sh:datatype} checks that the value is a valid lexical form of the datatype rather than
 * comparing datatype IRIs.</p>
 */
public final class ShaclValidator {
    private static final String NODE_KIND_IRI = Vocabulary.SH + "IRI";
    private static final String NODE_KIND_LITERAL = Vocabulary.SH + "Literal";

    private final Function<String, Optional<Entity>> entities;
    private final Function<String, Collection<String>> superClasses;

    /**
     * @param entities     resolves an entity IRI, used by {@code sh:class}
     * @param superClasses returns a class and all of its ancestors
     */
    public ShaclValidator(Function<String, Optional<Entity>> entities,
            Function<String, Collection<String>> superClasses) {
        this.entities = entities;
        this.superClasses = superClasses;
    }

    /** Value and closed-shape checks for a single statement. Cardinality needs the whole node. */
    public List<Violation> validateStatement(Collection<NodeShape> shapes, MetadataStatement statement) {
        List<Violation> violations = new ArrayList<>();
        for (NodeShape shape : shapes) {
            boolean declared = false;
            for (PropertyShape property : shape.properties()) {
                if (property.path().equals(statement.predicateUri())) {
                    declared = true;
                    checkValue(shape, property, statement, violations);
                }
            }
            if (shape.closed() && !declared && !shape.ignoredProperties().contains(statement.predicateUri())) {
                violations.add(new Violation(shape.uri(), statement.predicateUri(), statement.objectValue(),
                        "sh:closed", "Predicate is not allowed by closed shape " + shape.label()));
            }
        }
        return violations;
    }

    /** Full validation of a node: every statement plus cardinality. */
    public List<Violation> validateNode(Collection<NodeShape> shapes, Collection<MetadataStatement> statements) {
        List<Violation> violations = new ArrayList<>();
        for (MetadataStatement statement : statements) {
            violations.addAll(validateStatement(shapes, statement));
        }
        for (NodeShape shape : shapes) {
            for (PropertyShape property : shape.properties()) {
                long count = statements.stream()
                        .filter(statement -> statement.predicateUri().equals(property.path()))
                        .map(statement -> statement.objectIsUri() + "|" + statement.objectValue())
                        .distinct().count();
                if (property.minCount() != null && count < property.minCount()) {
                    violations.add(cardinality(shape, property, "sh:minCount",
                            "Expected at least " + property.minCount() + " value(s), found " + count));
                }
                if (property.maxCount() != null && count > property.maxCount()) {
                    violations.add(cardinality(shape, property, "sh:maxCount",
                            "Expected at most " + property.maxCount() + " value(s), found " + count));
                }
            }
        }
        return violations;
    }

    public boolean conforms(NodeShape shape, Collection<MetadataStatement> statements) {
        return validateNode(List.of(shape), statements).isEmpty();
    }

    private Violation cardinality(NodeShape shape, PropertyShape property, String constraint, String fallback) {
        return new Violation(shape.uri(), property.path(), null, constraint,
                property.message() != null ? property.message() : fallback);
    }

    private void checkValue(NodeShape shape, PropertyShape property, MetadataStatement statement,
            List<Violation> violations) {
        String value = statement.objectValue();
        check(violations, shape, property, statement, "sh:nodeKind", property.nodeKind(),
                kind -> NODE_KIND_IRI.equals(kind) ? statement.objectIsUri()
                        : !NODE_KIND_LITERAL.equals(kind) || !statement.objectIsUri());
        check(violations, shape, property, statement, "sh:datatype", property.datatype(),
                datatype -> !statement.objectIsUri() && isLexicalFormOf(datatype, value));
        check(violations, shape, property, statement, "sh:pattern", property.pattern(),
                pattern -> matches(pattern, property.flags(), value));
        check(violations, shape, property, statement, "sh:minLength", property.minLength(),
                min -> value.codePointCount(0, value.length()) >= min);
        check(violations, shape, property, statement, "sh:maxLength", property.maxLength(),
                max -> value.codePointCount(0, value.length()) <= max);
        check(violations, shape, property, statement, "sh:in", property.in(), allowed -> allowed.contains(value));
        check(violations, shape, property, statement, "sh:class", property.classUri(),
                classUri -> statement.objectIsUri() && entities.apply(value)
                        .map(entity -> superClasses.apply(entity.typeUri()).contains(classUri))
                        .orElse(false));
    }

    private <T> void check(List<Violation> violations, NodeShape shape, PropertyShape property,
            MetadataStatement statement, String constraint, T parameter, Predicate<T> test) {
        if (parameter != null && !test.test(parameter)) {
            String message = property.message() != null ? property.message()
                    : "Value does not satisfy " + constraint + " " + parameter;
            violations.add(new Violation(shape.uri(), property.path(), statement.objectValue(), constraint, message));
        }
    }

    private static boolean matches(String pattern, String flags, String value) {
        try {
            int options = 0;
            if (flags != null) {
                if (flags.contains("i")) options |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                if (flags.contains("s")) options |= Pattern.DOTALL;
                if (flags.contains("m")) options |= Pattern.MULTILINE;
                if (flags.contains("x")) options |= Pattern.COMMENTS;
            }
            // SHACL follows SPARQL REGEX semantics: an unanchored search.
            return Pattern.compile(pattern, options).matcher(value).find();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }

    private static boolean isLexicalFormOf(String datatype, String value) {
        if (!datatype.startsWith(RdfTerm.XSD)) {
            return true;
        }
        try {
            switch (datatype.substring(RdfTerm.XSD.length())) {
                case "integer", "int", "long", "nonNegativeInteger", "positiveInteger" ->
                        new java.math.BigInteger(value.startsWith("+") ? value.substring(1) : value);
                case "decimal" -> {
                    if (value.contains("e") || value.contains("E")) return false;
                    new java.math.BigDecimal(value);
                }
                case "double", "float" -> Double.parseDouble(value);
                case "boolean" -> {
                    return value.equals("true") || value.equals("false") || value.equals("1") || value.equals("0");
                }
                case "date" -> java.time.LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
                case "dateTime" -> java.time.format.DateTimeFormatter.ISO_DATE_TIME.parse(value);
                default -> { }
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
