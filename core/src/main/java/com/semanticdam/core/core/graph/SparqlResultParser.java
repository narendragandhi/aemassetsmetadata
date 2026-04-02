package com.semanticdam.core.core.graph;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;

import java.util.Optional;

public interface SparqlResultParser {
    Optional<AssetMetadata> parse(String resultBody, AssetId assetId);
}
