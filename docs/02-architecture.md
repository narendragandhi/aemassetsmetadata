# Architecture

## Bounded Contexts

- Asset Ingestion Context
- Ontology Governance Context
- Graph Storage Context
- Discovery and Search Context

## Domain Model Sketch

- AssetId value object for DAM asset paths.
- AssetMetadata aggregate with MetadataStatement facts.
- OntologyTerm for shared, reusable meaning.

## Knowledge Graph Stack

- Ontology format: RDF/OWL in Turtle for portability.
- Constraint validation: SHACL for future rule validation.
- Query language: SPARQL for graph reads.
- Storage options:
  - External RDF store for scale.
  - Local in-memory store for tests.

## AEM Integration Strategy

- AEM asset metadata extraction is an application layer concern.
- Domain objects remain storage-agnostic.
- Graph storage is an infrastructure adapter that can target a specific backend.

## External Metadata Ingestion

External systems can populate asset properties in AEM (for example via API or workflow). The ingestion flow is:

1. External system writes metadata to `/content/dam/.../jcr:content/metadata`.
1. `SlingResourceAemMetadataSource` reads raw properties.
1. `AemAssetMetadataMapper` normalizes and maps fields to ontology predicates.
1. `AssetMetadataPipelineService` writes triples to the knowledge graph adapter.

This keeps upstream variability isolated in the mapper while preserving a consistent graph shape.

## DDD and TDD Alignment

- Keep domain logic free of AEM APIs.
- Use adapter layers to translate AEM metadata to domain objects.
- Write tests at the domain and service boundary first, then implement adapters.

## Extension Points

- Mapping rules: `core/src/main/java/com/example/aemassets/core/application/AemAssetMetadataMapper.java`
- Metadata extraction: `core/src/main/java/com/example/aemassets/core/aem/SlingResourceAemMetadataSource.java`
- Graph storage adapter: `core/src/main/java/com/example/aemassets/core/graph/KnowledgeGraphService.java`
- Export format: `core/src/main/java/com/example/aemassets/core/graph/GraphSerializer.java`

## Service User Setup

The Sling-based metadata source requires a service user mapping for the subservice `aemassetsmetadata` to read `/content/dam`. Configure `org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended` with a system user that has read access to DAM metadata.

## Graph Storage Strategy

The current implementation uses an in-memory graph adapter for speed and testability. For production use, we will add a persistent adapter and select it via configuration.

### Candidate Backends

- RDF store (e.g., Apache Jena, GraphDB, Stardog) via SPARQL endpoints.
- Property graph store (e.g., Neo4j) with a translation layer from triples.
- AEM native storage using JCR nodes (least preferred for large-scale graph queries).

### Migration Plan

1. Implement a persistent adapter that implements `KnowledgeGraphService`.
1. Add configuration to choose the adapter at runtime.
1. Migrate the in-memory adapter to dev/test profiles only.
