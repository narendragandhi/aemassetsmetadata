package com.semanticdam.core.core.domain;

import java.util.Objects;

/** A reviewable AI assertion; proposals are never indexed automatically. */
public final class AiEnrichmentProposal {
    public enum Status { PROPOSED, APPROVED, REJECTED }

    private final AssetId assetId;
    private final MetadataStatement statement;
    private final String modelId;
    private final String promptVersion;
    private final String rationale;
    private final Status status;

    private AiEnrichmentProposal(AssetId assetId, MetadataStatement statement, String modelId,
                                 String promptVersion, String rationale, Status status) {
        if (assetId == null || statement == null) {
            throw new IllegalArgumentException("assetId and statement must be provided");
        }
        if (modelId == null || modelId.isBlank() || promptVersion == null || promptVersion.isBlank()) {
            throw new IllegalArgumentException("modelId and promptVersion must be provided");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must be provided");
        }
        if (statement.confidenceScore() < 0.0 || statement.confidenceScore() > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        this.assetId = assetId;
        this.statement = statement;
        this.modelId = modelId;
        this.promptVersion = promptVersion;
        this.rationale = rationale;
        this.status = status;
    }

    public static AiEnrichmentProposal proposed(AssetId assetId, MetadataStatement statement,
                                                 String modelId, String promptVersion, String rationale) {
        return new AiEnrichmentProposal(assetId, statement, modelId, promptVersion, rationale, Status.PROPOSED);
    }

    public AiEnrichmentProposal approve() {
        require(Status.PROPOSED);
        return new AiEnrichmentProposal(assetId, statement, modelId, promptVersion, rationale, Status.APPROVED);
    }

    public AiEnrichmentProposal reject() {
        require(Status.PROPOSED);
        return new AiEnrichmentProposal(assetId, statement, modelId, promptVersion, rationale, Status.REJECTED);
    }

    private void require(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("proposal is already " + status);
        }
    }

    public AssetId assetId() { return assetId; }
    public MetadataStatement statement() { return statement; }
    public String modelId() { return modelId; }
    public String promptVersion() { return promptVersion; }
    public String rationale() { return rationale; }
    public Status status() { return status; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AiEnrichmentProposal that)) return false;
        return Objects.equals(assetId, that.assetId) && Objects.equals(statement, that.statement)
                && Objects.equals(modelId, that.modelId) && Objects.equals(promptVersion, that.promptVersion)
                && Objects.equals(rationale, that.rationale) && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetId, statement, modelId, promptVersion, rationale, status);
    }
}
