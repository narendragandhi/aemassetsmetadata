package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;

public interface AssetMetadataPipeline {
    AssetMetadata ingest(AssetId assetId);
}
