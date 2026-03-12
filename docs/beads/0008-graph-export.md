# Bead 0008 - Graph Export

## Objective

Expose asset metadata as RDF Turtle to enable external graph ingestion.

## Tasks

- Define a triple model for graph serialization.
- Map `AssetMetadata` to triples.
- Serialize triples to Turtle.
- Provide unit tests for mapping and serialization.

## Acceptance Criteria

- Turtle output contains subject, predicate, and object values.
- Metadata maps consistently to triples.
- Tests pass with no external dependencies.
