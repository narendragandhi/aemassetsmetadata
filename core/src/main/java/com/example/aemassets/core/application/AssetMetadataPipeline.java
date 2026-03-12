package com.example.aemassets.core.application;

import com.example.aemassets.core.domain.AssetId;
import com.example.aemassets.core.domain.AssetMetadata;

public interface AssetMetadataPipeline {
    AssetMetadata ingest(AssetId assetId);
}
