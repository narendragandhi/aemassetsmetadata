# Bead 0003 - AEM Metadata Mapping

## Objective

Provide a mapping layer that converts AEM asset metadata into domain `AssetMetadata` statements.

## Tasks

- Add a mapper for core AEM metadata fields (dc:title, dc:description, dc:format).
- Map a sample business field (aem:brand).
- Provide unit tests for mapping behavior.

## Acceptance Criteria

- Mapping produces expected number of statements.
- No AEM APIs are required for the mapper.
- Tests pass in isolation.
