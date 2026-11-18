# BMAD Spec-Driven Delivery

## Working Definition

BMAD here stands for:

- Business intent: the user-visible outcome we optimize for.
- Model: the domain model, ontology, and invariants.
- Architecture: the structural and integration decisions.
- Delivery: the bead-level plan with tests and acceptance criteria.

This definition is used until you provide a different internal standard.

## Business Intent

Deliver a knowledge-graph-backed metadata layer for AEM Assets that enables richer discovery, governance, and reuse of asset semantics without disrupting authoring workflows.

## Model

- AssetId value object for DAM asset paths.
- AssetMetadata aggregate with MetadataStatement facts.
- OntologyTerm for canonical semantics.
- Ontology registry for resolving terms.

## Architecture

- Domain logic isolated in `core` and free of AEM APIs.
- AEM adapters translate asset metadata into domain objects.
- Knowledge graph adapter persists triples to the graph store.
- Ontology stored as Turtle resources, validated in tests.

## Delivery

- Beads under `docs/beads/` define tasks and acceptance criteria.
- Each bead has tests that run in CI.
- Gastown orchestration coordinates spec, domain, AEM adapters, graph, and tests.

## Risks and Mitigations

- Risk: Turtle parsing and SHACL correctness without an RDF library at runtime.
  Mitigation: full Turtle parser and a SHACL Core subset, both compared with Apache Jena in tests
  (decision D01).
- Risk: mismatched AEM metadata structures.
  Mitigation: define explicit metadata mapping contracts in the AEM adapter layer.

## Next Beads

See `beads/0024-context-engine.md` and the roadmap in `12-positioning-and-moat.md`.
