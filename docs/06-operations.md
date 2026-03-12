# Operations Guide

## Service User Mapping

Create a service user mapping so the metadata source can read DAM metadata.

- OSGi PID: `org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended`
- Subservice: `aemassetsmetadata`
- System user: `aem-assets-metadata-svc`

Example entry:

```
com.example.aemassets:aemassetsmetadata=aem-assets-metadata-svc
```

## ACLs

Grant read access to DAM paths:

- `/content/dam`
- `/content/dam/*/jcr:content/metadata`

## Endpoints

- `GET /bin/aemassets/metadata.json?path=/content/dam/...`
- `GET /bin/aemassets/metadata.ttl?path=/content/dam/...`

Example usage:

```
curl -s "http://localhost:4502/bin/aemassets/metadata.json?path=/content/dam/aemassetsmetadata/asset.jpg"
```

```
curl -s "http://localhost:4502/bin/aemassets/metadata.ttl?path=/content/dam/aemassetsmetadata/asset.jpg"
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

`core/src/main/java/com/example/aemassets/core/application/AemAssetMetadataMapper.java`

## Change Detection

`AssetMetadataResourceChangeListener` listens for DAM changes and triggers indexing. If you ingest metadata in bulk, use that listener or call the pipeline service directly.

## Notes

The in-memory graph store is for development only. Replace it with a persistent graph adapter for production.

## Graph Storage Mode

Configure `com.example.aemassets.core.graph.GraphStorageConfig`:

- `in-memory` for local dev and tests.
- `sparql` for external graph store.

Configure `com.example.aemassets.core.graph.SparqlEndpointConfig` with endpoint URL and credentials when using SPARQL mode.

## SPARQL Readback Status

The SPARQL adapter can write triples and perform a query, but result parsing is stubbed. Implement a real `SparqlResultParser` before relying on query responses in production.
