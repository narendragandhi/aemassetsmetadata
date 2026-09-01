# SemanticDAM — Product Narrative and Use Cases

## One-sentence positioning

SemanticDAM is a provider-neutral, human-governed metadata intelligence layer
for AEM Assets. It turns inconsistent DAM metadata and AI suggestions into
explainable, reusable facts that can power search, relationships, and
contextual content.

## The problem

AEM Assets often contains valuable information, but that information is hard
to reuse reliably. The same concept appears under different property names,
metadata is split between AEM and external systems, authors cannot tell which
value is authoritative, and AI enrichment cannot safely publish blindly.

The result is a DAM that stores content, but does not expose a dependable
semantic layer for the rest of the experience ecosystem.

## The answer

SemanticDAM introduces a governed fact layer alongside AEM metadata:

```text
AEM and external metadata → canonical predicates + provenance
                           → graph relationships and validation
                           → AI proposes missing facts
                           → steward approves or rejects
                           → approved facts become reusable
                           → JSON, Turtle, search, CCVAR, and downstream consumers
```

AI proposes; it does not become the source of truth. A fact is authoritative
only after it passes the configured policy and review boundary.

## Personas

### Product manager

Owns asset findability, reuse, metadata quality, and time-to-market. Needs a
clear outcome, measurable quality, and a rollout path that does not require
replatforming AEM.

### DAM or content operations lead

Owns metadata completeness and stewardship. Needs a queue of suggestions,
evidence, reasoned decisions, and safe correction of bad values.

### AEM solution architect

Owns platform fit, security, deployment, and integration complexity. Needs
stable contracts, OSGi seams, AEM-native storage, clear publish boundaries,
and replaceable AI, vector, and graph adapters.

### Experience developer

Needs dependable facts without copying metadata into every component or
writing a custom integration for every use case. Consumes normalized APIs,
graph queries, or contextual variables.

## Priority use cases

### 1. Governed metadata completion

An asset arrives with a title and image but no campaign, SKU, or brand fact.
The provider proposes candidates with confidence and rationale. A steward
reviews the proposal and approves only facts supported by evidence.

Outcome: higher metadata completeness without autonomous publishing risk.

### 2. Cross-asset discovery and relationships

Assets from different folders or source systems share a SKU, campaign, or
brand. Canonical predicates make those relationships queryable instead of
leaving them hidden in unrelated property names.

Outcome: better reuse, related-asset discovery, and campaign consistency.

### 3. Contextual content from governed facts

An experience needs to render a campaign or SKU without duplicating that value
into page content. An approved graph fact is exposed through:

```text
((semantic_properties.sku))
```

Outcome: less duplicated authoring and a clearer source of truth for rendered
content.

## MVP boundary

The first release should prove one narrow loop:

1. Read metadata from an AEM DAM asset.
2. Normalize it into canonical statements with provenance.
3. Ask one configured provider for bounded suggestions.
4. Persist proposals separately from authoritative graph facts.
5. Let an authenticated steward approve or reject with a reason.
6. Project approved facts into the graph.
7. Consume one approved fact through JSON and CCVAR.

The MVP does not need a full enterprise knowledge graph, autonomous publishing,
every model provider, bulk review, or a complete DAM replacement.

## Why this belongs in AEM

SemanticDAM uses AEM-native seams where appropriate:

- DAM paths and metadata resources remain the content source;
- OSGi services provide replaceable integration boundaries;
- JCR stores proposal and reference graph state for the baseline deployment;
- Sling servlets provide bounded author/demo APIs;
- Granite/Coral UI provides the review experience;
- Core Components and existing AEM rendering remain untouched;
- publish behavior is opt-in and can be kept separate from author governance.

This is an additive metadata capability, not a replacement for AEM Assets,
PIM, MDM, or a dedicated RDF platform.

## Architecture in one view

```text
 AEM DAM / external sources
             ↓
     mapper + validator
             ↓
       governed graph ← approved facts
             ↓                 ↓
    bounded provider       JSON/Turtle/CCVAR
             ↓                 ↓
       JCR proposal      AEM experiences
       + review UI
```

Vector search, Qdrant, OpenAI-compatible providers, SPARQL, and additional
Adobe or local providers are adapters around this spine.

## Demo story

Use a WKND or sample asset and tell the story in this order:

1. Show the asset’s ordinary AEM metadata.
2. Show normalized JSON facts and their sources.
3. Run `POST /bin/aemassets/ai/propose`.
4. Show confidence, model, and rationale in the review UI.
5. Approve one fact and reject another with reasons.
6. Show that only the approved fact appears in the graph response.
7. Render it through `((semantic_properties.sku))`.
8. Explain how the same fact supports related-asset or semantic search.

The proof is not that a model generated text. The proof is that an AEM team
can improve metadata safely and reuse the result across consumers.

## Value and success measures

| Outcome | Example measure |
|---|---|
| metadata completeness | percentage of assets with required predicates |
| steward efficiency | review minutes per accepted fact |
| suggestion quality | precision and recall by predicate family |
| trust | acceptance, rejection, and correction rates |
| reuse | consumers using graph-backed facts |
| operational safety | invalid facts and unauthorized decisions blocked |
| economics | provider cost per reviewed asset and accepted fact |

Do not use a single AI score as the product KPI. Accuracy must be measured by
predicate family and business risk.

## Adoption path

### Stage 1 — prove value

Use the in-memory graph for tests and a JCR-backed graph/proposal store in a
controlled Author environment. Enrich one asset family and one predicate group.

### Stage 2 — operationalize

Add durable audit records, permission review, evidence references, dead-letter
handling, cost dashboards, and a real AEM Author integration test.

### Stage 3 — scale selectively

Add bulk review, queue filtering, reprocessing, vector retrieval, additional
providers, and external graph storage only where measured demand justifies it.

### Stage 4 — publish safely

Enable selected read consumers, including CCVAR, only after dispatcher,
replication, cache variation, performance, and data governance review.

## Positioning and non-claims

SemanticDAM should be positioned as an AEM metadata governance and reuse layer.
It should not currently claim to be:

- a replacement for AEM Assets, PIM, MDM, or Content Intelligence;
- an autonomous metadata publishing system;
- a complete OWL reasoning or enterprise RDF platform;
- a certified compliance or provenance system;
- a universal AI gateway or finished DAM review product.

Its differentiation is the combination of AEM-native integration, governed AI
approval, provenance-aware semantic facts, and reusable experience consumption
in one replaceable architecture.

## Reference implementation status

The repository contains the core contracts, JCR proposal persistence, provider
seams, author review component, CCVAR-compatible resolver, vector adapter, and
repeatable tests. See [Show and Tell](07-show-and-tell.md) for the runnable demo
and [AI Readiness](08-ai-readiness.md) for the safety boundaries.
