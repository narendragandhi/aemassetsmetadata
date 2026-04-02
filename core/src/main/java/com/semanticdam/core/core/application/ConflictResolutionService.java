package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import java.util.Collection;

/**
 * Service responsible for resolving metadata conflicts between different sources
 * based on architectural precedence rules.
 */
public interface ConflictResolutionService {
    
    /**
     * Resolves conflicts within the provided metadata and returns only the 
     * authoritative statements for each predicate.
     * 
     * @param metadata the raw metadata with potential conflicts/multiple sources
     * @return a collection of authoritative metadata statements
     */
    Collection<MetadataStatement> resolve(AssetMetadata metadata);
}
