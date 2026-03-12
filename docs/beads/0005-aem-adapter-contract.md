# Bead 0005 - AEM Adapter Contract

## Objective

Define the AEM metadata source contract and a pipeline that ingests metadata into the knowledge graph.

## Tasks

- Define `AemMetadataSource` for reading AEM metadata.
- Provide a map-backed implementation for tests.
- Add a pipeline service that extracts and indexes metadata.
- Provide unit tests for the pipeline.

## Acceptance Criteria

- Pipeline ingests metadata and persists it to the graph adapter.
- No AEM APIs required for tests.
- Adapter contract is stable for later AEM Resource-based implementation.
