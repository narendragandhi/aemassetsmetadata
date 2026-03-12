# Bead 0017 - Graph Adapter Selection

## Objective

Provide configuration-based selection of the knowledge graph adapter.

## Tasks

- Implement a provider that selects in-memory vs SPARQL adapter based on config.
- Add a SPARQL provider that wires endpoint config into the store.
- Provide tests for default behavior.

## Acceptance Criteria

- Default mode uses in-memory store.
- SPARQL config can be wired without impacting default mode.
