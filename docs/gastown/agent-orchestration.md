# Gastown Agent Orchestration

## Agents

- Spec Agent for requirements and BMAD spec.
- Domain Agent for DDD model and invariants.
- AEM Adapter Agent for asset extraction and AEM integration.
- Graph Agent for ontology and KG storage design.
- Test Agent for TDD coverage and quality gates.

## Coordination Flow

1. Spec Agent produces or updates the BMAD spec.
1. Domain Agent extracts aggregates, value objects, and invariants.
1. Graph Agent defines ontology updates and storage adapters.
1. AEM Adapter Agent maps AEM metadata to domain objects.
1. Test Agent validates tests and gates for each bead.

## Artifacts

- `docs/03-bmad-spec.md` as the single source of truth.
- `docs/beads/*.md` for work breakdown.
- `docs/LOG.md` for execution trace.
