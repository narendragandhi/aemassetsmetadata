# Bead 0003 - Visual Magic & AI Vision

## Objective
Expose the Knowledge Graph intelligence through visual UI components, automated AI vision tagging, and SEO-ready semantic injection.

## Tasks
- **AI Vision Pipeline**: Create `SemanticVisionWorkflowProcess` to simulate/integrate AI scene intelligence (Pixels -> Triples).
- **JSON-LD SEO Provider**: Implement `SemanticSeoService` to inject Knowledge Graph metadata into AEM pages as JSON-LD.
- **The Semantic Rail (UI)**: Scaffold the Granite UI components for an AEM Assets side-panel to visualize relationships.
- **Dynamic Content Assembly**: Implement a "Smart Hero" Sling Model that chooses assets based on graph confidence and usage triples.

## Acceptance Criteria
- AEM Assets workflow can be triggered to generate "Visual Feature" triples.
- AEM Pages rendering assets include `<script type="application/ld+json">` containing graph data.
- UI components compile and are correctly registered in `ui.apps`.
- Build succeeds with Java 21 enforcement.
