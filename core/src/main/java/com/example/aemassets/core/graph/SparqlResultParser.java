package com.example.aemassets.core.graph;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;

import java.util.Optional;

public interface SparqlResultParser {
    Optional<AssetMetadata> parse(String resultBody, AssetId assetId);
}
