# Bead 0004 - Graph Adapter

## Objective

Provide the first knowledge-graph storage adapter and indexing service to persist asset metadata statements.

## Tasks

- Implement an in-memory knowledge graph store for local and test usage.
- Add an indexing service that writes `AssetMetadata` to the graph.
- Provide tests for indexing behavior.

## Acceptance Criteria

- `AssetMetadataIndexingService` writes to the graph adapter.
- Tests pass without external graph dependencies.
- Adapter can be swapped later for a real graph store.
