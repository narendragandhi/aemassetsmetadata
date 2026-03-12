# Bead 0016 - SPARQL Adapter Stub

## Objective

Provide a minimal SPARQL adapter stub that can persist metadata via SPARQL Update.

## Tasks

- Define a SPARQL client contract and HTTP implementation.
- Implement a SPARQL-backed `KnowledgeGraphService` that builds INSERT updates.
- Add unit tests for update generation.

## Acceptance Criteria

- SPARQL update string includes graph URI when configured.
- Adapter can be wired with endpoint credentials later.
