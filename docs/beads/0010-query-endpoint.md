# Bead 0010 - Query Endpoint

## Objective

Provide a basic read API that returns asset metadata as JSON for quick validation and integration testing.

## Tasks

- Add a query service over the graph adapter.
- Add a Sling servlet at `/bin/aemassets/metadata.json`.
- Provide tests for query service behavior.

## Acceptance Criteria

- GET with `path` parameter returns JSON metadata.
- Missing asset returns 404.
- Missing path returns 400.
