package com.semanticdam.core.core.domain;

import java.util.Objects;

public final class AssetId {
    private final String path;

    private AssetId(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("asset path must be provided");
        }
        this.path = path;
    }

    public static AssetId of(String path) {
        return new AssetId(path);
    }

    public String path() {
        return path;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AssetId)) {
            return false;
        }
        AssetId that = (AssetId) other;
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        return path;
    }
}
