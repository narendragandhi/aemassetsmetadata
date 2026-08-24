# Operations Guide

## Service User Mapping

Create a service user mapping so the metadata source can read DAM metadata.

- OSGi PID: `org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended`
- Subservice: `semanticdam`
- System user: `aem-assets-metadata-svc`

Example entry:

```
com.semanticdam.core:semanticdam=aem-assets-metadata-svc
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

## Graph Storage Mode

Configure `com.semanticdam.core.core.graph.GraphStorageConfig`:

- `in-memory` for local dev and tests.
- `sparql` for external graph store.

Configure `com.semanticdam.core.core.graph.SparqlEndpointConfig` with endpoint URL and credentials when using SPARQL mode.

## SPARQL Readback Status

The SPARQL adapter can write triples and build an asset read query, but result parsing, predicate search, and related-asset traversal are experimental. Implement and integration-test a real `SparqlResultParser` against the selected triplestore before enabling `sparql` mode in production.
