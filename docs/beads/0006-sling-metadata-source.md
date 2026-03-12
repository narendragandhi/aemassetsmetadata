# Bead 0006 - Sling Metadata Source

## Objective

Provide the first AEM-aware metadata source that reads from the asset metadata node using Sling APIs.

## Tasks

- Implement a Sling-based `AemMetadataSource` that reads `/jcr:content/metadata`.
- Provide unit tests using AEM mock context.
- Keep the adapter isolated from domain logic.

## Acceptance Criteria

- Reads metadata from a DAM asset path using Sling APIs.
- Tests pass using AEM mock context.
- Adapter can be replaced or extended without changing domain model.
