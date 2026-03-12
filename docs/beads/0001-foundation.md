# Bead 0001 - Foundation

## Objective

Establish the AEM project baseline and domain model scaffolding for knowledge-graph metadata.

## Tasks

- Generate the repo from the latest AEM Project Archetype.
- Enforce Java 21 at the Maven root.
- Add core domain objects and unit tests.
- Add a baseline asset metadata ontology file.

## Acceptance Criteria

- `mvn clean test` succeeds.
- Domain objects compile under Java 21.
- Ontology file exists and is readable from the classpath.
