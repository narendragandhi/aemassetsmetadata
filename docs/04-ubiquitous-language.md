# Ubiquitous Language

## Core Terms

- Asset: A DAM asset stored under `/content/dam`.
- Asset Metadata: The set of facts and relationships attached to an Asset.
- Metadata Statement: A predicate plus object describing a single fact.
- Ontology Term: A canonical definition of a concept or predicate.
- Knowledge Graph: A graph of assets, predicates, and shared entities.

## Relationships

- Asset has Metadata Statements.
- Metadata Statements reference Ontology Terms.
- Knowledge Graph stores Asset Metadata as triples.

## Invariants

- Every Asset Metadata set must include an AssetId.
- Every Ontology Term must have a URI and label.
- Metadata Statements must provide a predicate URI and object value.
