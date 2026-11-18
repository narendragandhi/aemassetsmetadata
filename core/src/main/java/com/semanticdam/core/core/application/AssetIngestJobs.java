package com.semanticdam.core.core.application;

/** Sling job contract shared by the change listener, the reindex endpoint and the consumer. */
public final class AssetIngestJobs {
    public static final String TOPIC = "com/semanticdam/asset/ingest";
    /** Queued after a successful ingest or removal while semantic search is configured. */
    public static final String EMBED_TOPIC = "com/semanticdam/asset/embed";
    public static final String PROPERTY_PATH = "path";
    public static final String PROPERTY_ACTION = "action";
    public static final String ACTION_INGEST = "ingest";
    public static final String ACTION_REMOVE = "remove";

    private AssetIngestJobs() {
    }
}
