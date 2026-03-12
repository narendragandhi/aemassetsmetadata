package com.example.aemassets.core.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AssetMetadata {
    private final AssetId assetId;
    private final List<MetadataStatement> statements;

    public AssetMetadata(AssetId assetId, List<MetadataStatement> statements) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must be provided");
        }
        if (statements == null) {
            throw new IllegalArgumentException("statements must be provided");
        }
        this.assetId = assetId;
        this.statements = new ArrayList<>(statements);
    }

    public static AssetMetadata empty(AssetId assetId) {
        return new AssetMetadata(assetId, Collections.emptyList());
    }

    public AssetId assetId() {
        return assetId;
    }

    public List<MetadataStatement> statements() {
        return Collections.unmodifiableList(statements);
    }

    public AssetMetadata add(MetadataStatement statement) {
        if (statement == null) {
            throw new IllegalArgumentException("statement must be provided");
        }
        List<MetadataStatement> updated = new ArrayList<>(statements);
        updated.add(statement);
        return new AssetMetadata(assetId, updated);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AssetMetadata)) {
            return false;
        }
        AssetMetadata that = (AssetMetadata) other;
        return Objects.equals(assetId, that.assetId) && Objects.equals(statements, that.statements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetId, statements);
    }

    @Override
    public String toString() {
        return "AssetMetadata{" + assetId + ", statements=" + statements.size() + "}";
    }
}
