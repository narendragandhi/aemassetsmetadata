# AI Readiness

## Current capability

SemanticDAM is AI-ready as a governed knowledge layer. It is not yet an autonomous AI enrichment product.

The current implementation provides:

- `GroundedAssetContext`: bounded asset facts, related assets, and ontology version supplied to a provider;
- `AiEnrichmentProvider`: provider-neutral seam for Adobe, hosted, or local model implementations;
- `AiEnrichmentProposal`: model/prompt provenance, rationale, confidence, and lifecycle status;
- `AiEnrichmentReviewService`: only explicitly approved proposals can become indexed metadata;
- `AiProposalEvaluator`: deterministic exact-match precision/recall checks for provider and prompt fixtures;
- `AiEnrichmentPolicy`: max-count, predicate-allowlist, and minimum-confidence gates;
- `AiProposalStore` and `AiProposalReviewService`: a persistence/audit seam that keeps review state separate from graph facts;
- `AiEnrichmentProviderRegistry`: explicit provider selection by stable id;
- `QdrantVectorSearchIndex`: self-hostable vector-index adapter using Qdrant’s
  HTTP API and model/version payload metadata;
- `ResilientAiEnrichmentProvider`: bounded retry, circuit breaker, rate limit,
  and telemetry decorator, including configurable estimated input/output cost;
- existing source, validity, and confidence fields on normalized statements.

The existing `InferenceService` remains deterministic rule logic. `SemanticVisionWorkflowProcess` remains a simulation and must not be described as a live model integration.

## Safety boundary

```text
AEM metadata + bounded graph context
              |
              v
     AI provider proposes facts
              |
              v
 proposal + model + prompt + rationale + confidence
              |
              v
     evaluation and human/system approval
              |
              v
       normalized graph projection
```

An AI provider must not write directly to `KnowledgeGraphService`. This prevents unreviewed hallucinations or model regressions from becoming authoritative repository data.

## Provider contract

Implementations of `AiEnrichmentProvider` should:

1. accept only a bounded `GroundedAssetContext`;
2. use the ontology version supplied by the context;
3. return canonical predicates, not source-specific property names;
4. include a stable model identifier and prompt version;
5. include a concise evidence-based rationale;
6. set confidence between `0.0` and `1.0`;
7. return proposals, never write to AEM;
8. fail closed on provider timeout, malformed output, or policy violation.

## Grounding rules

Grounding context should be assembled from:

- the requested asset’s normalized metadata;
- explicitly allowed related assets;
- ontology labels, descriptions, and validation patterns;
- approved asset renditions or extracted text where rights permit.

Do not send unrestricted repository trees, arbitrary user content, secrets, service credentials, or unrelated asset data to a model.

Every model response should be traceable to:

- asset path;
- context/ontology version;
- model identifier;
- prompt version;
- provider request identifier;
- proposal status;
- approval actor and timestamp once approval storage is added.

## Evaluation

The evaluator currently supports exact predicate/object matching and reports:

- true positives;
- false positives;
- false negatives;
- precision;
- recall.

Before enabling a provider, add fixture cases for:

- correct classification;
- ambiguous visual content;
- missing evidence;
- invalid ontology predicate;
- duplicate or conflicting source facts;
- prompt/model version changes;
- adversarial or instruction-injection content;
- Unicode, long text, and malformed provider output.

Production gates should define minimum precision and recall per predicate family. A single aggregate score is not sufficient for rights, legal, safety, or publish-visible metadata.

## Next implementation phases

### Phase A: controlled provider integration

- `AiEnrichmentPolicy`, `AiProposalStore`, and `AiProposalReviewService` now define the provider-control and review seams;
- add an OSGi configuration for provider selection, timeout, max proposals, and minimum confidence;
- implement one provider behind `AiEnrichmentProvider`;
- the first concrete adapter is `openai-compatible`; additional providers
  register behind the same interface and are selected by provider id;
- replace the in-memory proposal store with a JCR-backed store;
- add evidence references and provider request identifiers to persisted proposal records;
- add redaction and payload-size limits.

### Phase B: retrieval

- `EmbeddingVector` carries model id and version, while `EmbeddingProvider`,
  `VectorSearchIndex`, and `InMemoryVectorSearchIndex` define the
  provider/index seam;
- `HybridAssetRetriever` now demonstrates vector recall plus an exact governed
  graph-filter boost;
- the in-memory index is a reference implementation for tests and demos only:
  it is linear, non-persistent, and skips incompatible dimensions;
- Qdrant is the first open-source production adapter; create the collection
  with the embedding dimension and cosine distance before indexing;
- both live adapters are opt-in OSGi services with required configuration, so
  an unconfigured deployment cannot accidentally call a model or vector store;
- production adapters must store embedding metadata with model/version, not
  opaque vectors alone, and must define deletion/re-embedding behavior;
- evaluate retrieval recall before using it as model context;
- define deletion and re-embedding behavior when assets or models change.

### Phase C: human-in-the-loop authoring

- expose persisted proposals in an author review workflow;
- show source evidence and rationale;
- allow approve, reject, edit, and defer;
- require permission checks and audit history;
- keep proposal data separate from authoritative metadata until approval; the current review service enforces this at the application boundary.

### Phase D: production operations

- provider health, latency, token/cost, and failure metrics;
- model quality dashboards and drift alerts;
- rate limits, retries, circuit breakers, and dead-letter handling;
- privacy, rights, retention, and data residency review;
- reproducible prompt/model evaluation in CI.

## Explicit non-claims

Until the phases above are completed, do not claim that the repository provides:

- a production model connector;
- production vector search (the repository includes only a bounded in-memory
  reference index and hybrid policy);
- autonomous metadata publishing;
- model quality guarantees;
- complete prompt-injection protection;
- enterprise AI governance or compliance certification.
