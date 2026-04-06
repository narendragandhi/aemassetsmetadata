# Bead 0004 - The Opinionated Entity Framework

## Objective
Adopt Drupal's "Structured Data" philosophy by implementing strict entity bundles, taxonomy integrity, and a relationship-driven "Views" service.

## Tasks
- **Entity Bundle Validator**: Implement `OntologyEnforcementService` to reject triples that don't match the allowed schema for a specific asset type.
- **Taxonomy Integrity Layer**: Upgrade `OntologyRegistry` to enforce "Entity References" for taxonomic terms (no more "String" tags).
- **Semantic View Service**: Create `GraphRelationshipQueryService` (The "Views" for AEM) to provide high-level relationship queries for HTL components.
- **Ontology Extension**: Add "Usage Rights" and "Talent" bundles to the graph to demonstrate strict multi-entity management.

## Acceptance Criteria
- Triples with unknown predicates are flagged or rejected.
- Taxonomic triples must point to valid SKOS terms.
- `HelloWorldModel` can use the "View Service" to fetch "Assets by Category" without writing a single line of JCR Query code.
- Build succeeds with Java 21.
