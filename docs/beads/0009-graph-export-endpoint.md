# Bead 0009 - Graph Export Endpoint

## Objective

Expose a simple Sling endpoint that returns Turtle for an asset path.

## Tasks

- Create an export service that maps stored metadata to Turtle.
- Wire an OSGi component for the export service.
- Add a Sling servlet endpoint at `/bin/aemassets/metadata.ttl`.
- Provide unit tests for export behavior.

## Acceptance Criteria

- GET with `path` parameter returns Turtle output.
- Missing asset returns 404.
- Missing path returns 400.
