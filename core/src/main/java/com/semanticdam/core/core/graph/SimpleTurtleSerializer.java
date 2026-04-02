package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import java.util.List;

public class SimpleTurtleSerializer implements GraphSerializer {
    
    @Override
    public String toTurtle(List<GraphTriple> triples) {
        StringBuilder sb = new StringBuilder();
        sb.append("@prefix aem: <https://example.com/aem-assets-ontology#> .\n");
        sb.append("@prefix dc: <http://purl.org/dc/terms/> .\n\n");

        for (GraphTriple triple : triples) {
            sb.append("<").append(triple.subject()).append("> ")
              .append("<").append(triple.predicate()).append("> ")
              .append(formatObject(triple)).append(" .\n");
        }
        return sb.toString();
    }

    public String serialize(AssetMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("@prefix aem: <https://example.com/aem-assets-ontology#> .\n");
        sb.append("@prefix dc: <http://purl.org/dc/terms/> .\n\n");

        for (MetadataStatement statement : metadata.statements()) {
            sb.append("<").append(metadata.assetId().path()).append("> ")
              .append("<").append(statement.predicateUri()).append("> ")
              .append(formatObject(statement)).append(" .\n");
        }
        return sb.toString();
    }

    private String formatObject(GraphTriple triple) {
        if (triple.objectIsUri()) {
            return "<" + triple.object() + ">";
        } else {
            return "\"" + escape(triple.object()) + "\"";
        }
    }

    private String formatObject(MetadataStatement statement) {
        if (statement.objectIsUri()) {
            return "<" + statement.objectValue() + ">";
        } else {
            return "\"" + escape(statement.objectValue()) + "\"";
        }
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\"", "\\\"");
    }
}
