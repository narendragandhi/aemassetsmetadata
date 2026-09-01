# Operations Guide

## Service User Mapping

Create a service user mapping so the metadata source can read DAM metadata.

- OSGi PID: `org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended`
- Subservice: `semanticdam`
- System user: `aem-assets-metadata-svc`

Example entry:

```
semanticdam.core:semanticdam=[aem-assets-metadata-svc]
```

## ACLs

Grant read access to DAM paths:

- `/content/dam`
- `/content/dam/*/jcr:content/metadata`

## Endpoints

These endpoints are intended for an authenticated author/demo environment. Do not expose them through a public dispatcher farm until authorization, filtering, and cache policy have been reviewed.

- `GET /bin/aemassets/metadata.json?path=/content/dam/...`
- `GET /bin/aemassets/metadata.ttl?path=/content/dam/...`

Example usage:

```
curl -s "http://localhost:4502/bin/aemassets/metadata.json?path=/content/dam/semanticdam/asset.jpg"
```

```
curl -s "http://localhost:4502/bin/aemassets/metadata.ttl?path=/content/dam/semanticdam/asset.jpg"
```

The JSON response is the normalized, ontology-backed representation. Turtle output is suitable for graph tools.

For a ready-to-run demo against the included asset, use `bash demo/curl-demo.sh`.

For an install verification that waits for the change listener and checks both output formats, use `bash scripts/verify-aem-sdk.sh`.

## External Metadata Ingestion

When metadata is populated by external systems, store it under the asset metadata node:

- Path: `/content/dam/<asset>/jcr:content/metadata`
- Common fields consumed by the mapper today:
  - `dc:title`
  - `dc:description`
  - `dc:format`
  - `aem:brand`

If the external system uses different field names, update the mapping rules in:

`core/src/main/java/com.semanticdam.core/core/application/AemAssetMetadataMapper.java`

## Change Detection

`AssetMetadataResourceChangeListener` listens for DAM changes and triggers indexing. If you ingest metadata in bulk, use that listener or call the pipeline service directly.

## Notes

The in-memory graph store is for development and tests only. The JCR adapter is the verified persistent reference path. It stores each statement as a child node so duplicate predicates and provenance fields are retained.

For a local vector backend, start `docker compose -f infra/docker-compose.vector.yml up -d`. Qdrant is intentionally an external service: configure TLS, API-key authentication, persistent storage, backups, resource limits, and network policy before production use. The `QdrantVectorSearchIndex` expects a pre-created collection whose vector size and cosine distance match the configured embedding model.

### AI provider and vector-index OSGi wiring

The bundle contains two opt-in OSGi services. They use `configurationPolicy=REQUIRE`, so neither activates until an environment supplies configuration:

- `com.semanticdam.core.core.application.OpenAiCompatibleProviderConfig` — set `enabled=true`, endpoint, password-type bearer token, model, allowed predicates, and resilience/cost settings. The service id is `openai-compatible`.
- `com.semanticdam.core.core.application.QdrantVectorIndexConfig` — set the Qdrant URL, collection, model id/version, and timeout. The collection must already exist with matching vector dimension and cosine distance.

Provide these through the deployment environment’s OSGi configuration mechanism (for example, an AEM as a Cloud Service environment configuration or a protected run-mode package). Do not commit `apiKey` values. With no configuration, AEM continues using the existing JCR graph path and no AI provider is callable.

AI proposals use a separate `JcrAiProposalStore` path under `/var/semanticdam/proposals`. This prevents proposed facts from being confused with authoritative graph facts. The same service user has read/write access to both controlled paths; expose proposal records only through an authenticated author review surface.

## Graph Storage Mode

Configure `com.semanticdam.core.core.graph.GraphStorageConfig`:

- `in-memory` for local dev and tests.
- `sparql` for external graph store.

Configure `com.semanticdam.core.core.graph.SparqlEndpointConfig` with endpoint URL and credentials when using SPARQL mode.

## SPARQL Readback Status

The SPARQL adapter can write triples and build an asset read query, but result parsing, predicate search, and related-asset traversal are experimental. Implement and integration-test a real `SparqlResultParser` against the selected triplestore before enabling `sparql` mode in production.
