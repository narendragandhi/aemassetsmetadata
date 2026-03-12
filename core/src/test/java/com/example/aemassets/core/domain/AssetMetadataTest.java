package com.example.aemassets.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetMetadataTest {
    @Test
    void requiresAssetId() {
        assertThrows(IllegalArgumentException.class, () -> new AssetMetadata(null, java.util.List.of()));
    }

    @Test
    void addsStatementsImmutably() {
        AssetId assetId = AssetId.of("/content/dam/brand/logo.png");
        AssetMetadata metadata = AssetMetadata.empty(assetId);

        MetadataStatement statement = MetadataStatement.literal("http://schema.org/name", "Logo");
        AssetMetadata updated = metadata.add(statement);

        assertEquals(0, metadata.statements().size());
        assertEquals(1, updated.statements().size());
        assertEquals(statement, updated.statements().get(0));
    }
}
