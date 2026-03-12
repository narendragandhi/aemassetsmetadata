# Bead 0013 - Endpoint Tests

## Objective

Add lightweight servlet-level tests for the JSON and Turtle endpoints using AEM mock.

## Tasks

- Add a test for `/bin/aemassets/metadata.json`.
- Add a test for `/bin/aemassets/metadata.ttl`.
- Use in-memory graph store to seed data.

## Acceptance Criteria

- Endpoint tests assert content type and body content.
- Tests run without a real AEM instance.
