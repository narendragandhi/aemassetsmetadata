# Bead 0024 - Context engine

## Objective
Make the ontology the single source of truth, turn the statement store into a graph with entity
nodes, and serve asset context to applications and agents with access control.

## Delivered
- Turtle parser, ontology model and SHACL validator, checked against Apache Jena in tests.
- Ontology-driven mapping, validation, inference and bundles; ontology extensions from the repository.
- Entity nodes, related-asset ranking with reasons, shortest-path search.
- Statement ownership by source so reviewed AI facts survive re-ingest.
- `context.json`, the MCP endpoint, the reindex endpoint; caller-based read filtering everywhere.
- Durable AI review audit trail; source of AI statements set by the server.
- Oak index, corrected OSGi configuration and service-user mapping.

## Acceptance Criteria
- `mvn clean install` passes, including the AEM analyser. Met.
- `scripts/verify-aem-sdk.sh` passes against a local AEM SDK. Met on 2026-11-17 (SDK 2026.7).

## Not in scope
OWL reasoning, a graph explorer UI (see bead 0023). Vector search, the SPARQL read path and AEM tag concepts were added on 2026-11-17.
