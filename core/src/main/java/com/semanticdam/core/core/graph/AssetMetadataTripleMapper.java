package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;

import java.util.ArrayList;
import java.util.List;

public class AssetMetadataTripleMapper {
    private final GraphSubjectResolver subjectResolver;

    public AssetMetadataTripleMapper() {
        this(new GraphSubjectResolver());
    }

    public AssetMetadataTripleMapper(GraphSubjectResolver subjectResolver) {
        if (subjectResolver == null) {
            throw new IllegalArgumentException("subjectResolver must be provided");
        }
        this.subjectResolver = subjectResolver;
    }

    public List<GraphTriple> toTriples(AssetMetadata metadata) {
        List<GraphTriple> triples = new ArrayList<>();
        String subject = subjectResolver.assetSubject(metadata.assetId());
        for (MetadataStatement statement : metadata.statements()) {
            triples.add(new GraphTriple(
                    subject,
                    statement.predicateUri(),
                    statement.objectValue(),
                    statement.objectIsUri()
            ));
        }
        return triples;
    }
}
