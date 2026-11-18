# Ubiquitous Language

## Core Terms

- Asset: A DAM asset stored under `/content/dam`.
- Asset Metadata: The set of facts and relationships attached to an Asset.
- Metadata Statement: A predicate plus object describing a single fact.
- Ontology Term: A canonical definition of a concept or predicate.
- Knowledge Graph: A graph of assets, predicates, and shared entities.
- Entity: A node other than an asset (brand, product, campaign, taxonomy concept) with an IRI, a class and a label.
- Source System: Where a statement came from (PIM, Workfront, AEM, `Inference-Engine`, `AI:<model>`), with a trust level.
- Shape: A SHACL description of what a valid asset or entity looks like. A closed shape defines a bundle.
- Readiness Shape: A shape that, once an asset conforms to it, raises a "ready for <consumer>" event.
- Authoritative View: The statements left after conflict resolution; one value per single-valued predicate.
- Asset Context: The document an agent receives about an asset: facts with provenance, entities, related assets and why, violations, readiness.
- Proposal: An AI-suggested statement awaiting human review. It is not in the graph until approved.
- Domain Ontology: The classes, properties, agents and bundled taxonomy, each with a definition.
- Application Profile: How a deployment uses the ontology: mappings, trust, shapes, rules, vocabulary entries.
- Controlled Vocabulary Entry: An entity with a status: APPROVED, CANDIDATE, REJECTED or MERGED.
- Candidate: An entry created from a value whose source is not the authority; links to it are invalid until a steward approves it.
- Vocabulary Steward: A member of `semanticdam-vocabulary-stewards` who approves, rejects or merges candidates.
- Concept Scheme: A set of concepts managed together; each AEM tag namespace is one.
- Broader Subject: A concept an asset is about only because a subject is narrower than it; derived, used for search, not for relatedness.
- Agent: The PROV-O identity a statement is attributed to (a source system, the inference engine, an AI model).

## Relationships

- Asset has Metadata Statements.
- Metadata Statements reference Ontology Terms.
- Knowledge Graph stores Asset Metadata as triples.

## Invariants

- Every Asset Metadata set must include an AssetId.
- Every Ontology Term must have a URI and label.
- Metadata Statements must provide a predicate URI and object value.
