# Bead 0019 - SPARQL Readback

## Objective

Add a SPARQL query builder and a readback contract for retrieving asset metadata from a graph store.

## Tasks

- Add a SPARQL query builder for asset triples.
- Add a result parser contract (no-op implementation to start).
- Update SPARQL store to use query builder and parser.
- Add tests for query construction.

## Acceptance Criteria

- SPARQL queries include the subject and graph clause when configured.
- Result parser is injectable for future implementation.
