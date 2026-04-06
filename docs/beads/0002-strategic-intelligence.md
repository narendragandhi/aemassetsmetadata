# Bead 0002 - Strategic Intelligence

## Objective
Transform the Knowledge Graph from a storage layer into a "Reasoning Engine" with provenance-aware authority and predictive UI.

## Tasks
- **Inference Logic**: Implement `InferenceService` to derive new facts (e.g., SKU -> Sustainable Category).
- **Self-Healing Conflict Resolver**: Add `AuthorityService` to auto-correct DAM properties using Confidence Scores.
- **Semantic Rail UI Scaffolding**: Create Granite UI side-panel component to visualize relationships.
- **Interoperability Polish**: Finalize mapping to Schema.org and SKOS (Kurt Cagle compliance).

## Acceptance Criteria
- Unit tests verify derived inferences (Facts inferred > Facts stored).
- Conflict resolver reverts low-confidence manual overrides against high-confidence PIM data.
- Side-panel component renders in AEM Assets UI (Mocked or real graph data).
- Build succeeds with Java 21 enforcement.
