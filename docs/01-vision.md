# Vision

## Problem Statement

AEM asset metadata today is often stored as flat key-value fields. This limits discoverability, reuse, and governance. We want metadata that behaves like a knowledge graph with explicit semantics, shared ontology, and cross-asset relationships.

## Goals

- Model asset metadata as a knowledge graph with ontology-aligned predicates.
- Support both literal metadata and relationships to shared entities like brands, licenses, and campaigns.
- Keep AEM authoring simple while enabling richer search and governance.
- Provide a path to external graph storage without locking into a single vendor.
- Normalize and govern metadata arriving from external systems once it lands in AEM.

## Positioning

An accelerator that complements AEM Assets' native AI (AI Search, GenAI metadata, Content
Discovery agent, AEM MCP Server). See [12-positioning-and-moat.md](12-positioning-and-moat.md).

## Non-Goals

- Replacing AEM Assets, AEM AI Search or any Adobe AI capability.
- Building a full DAM UI. We focus on metadata, not UI customization.
- One-off, siloed metadata schemas per project.

## Success Criteria

- A clear DDD boundary that separates AEM ingestion from graph storage.
- A baseline ontology for asset metadata that can be extended safely.
- TDD-friendly services and domain objects with unit tests.
- Spec-driven delivery with BMAD and beads.
- Measurable improvement in the facts Adobe's AI features and agents work on (see the roadmap in
  the positioning doc).
- Documented “show and tell” flow that demonstrates authoring and external metadata ingestion.
