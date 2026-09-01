# Product Requirements Document

## SemanticDAM: AEM Asset Metadata Governance and Relationship Service

Version: 1.0  
Status: Proposed production baseline  
Audience: Product owners, information architects, AEM architects, developers, security engineers, QA, and operations teams

## 1. Executive summary

SemanticDAM is an AEM-native service that turns scattered DAM metadata into a governed, queryable asset information layer.

It reads metadata from AEM Assets, maps source-specific properties to canonical ontology predicates, preserves provenance and confidence, applies conflict-resolution rules, and exposes stable APIs for asset metadata and relationships.

The first production release is intentionally narrow:

> A secure, JCR-backed AEM service for normalized asset metadata and relationship queries.

External triplestores, AI enrichment, graph visualization, and direct PIM/Workfront connectors are extension tracks. They must not be treated as prerequisites for the first production release.

## 2. Problem statement

Enterprise AEM implementations commonly contain metadata from multiple systems:

- AEM authors and DAM workflows
- Product information management systems
- Workfront or campaign systems
- Brand, legal, localization, and rights-management processes
- Automated enrichment such as vision or classification services

The same business fact may appear under different property names, have conflicting values, or lack an accountable source. Downstream consumers then build one-off mappings and queries, which causes:

- inconsistent meaning across teams;
- unclear ownership and provenance;
- brittle integrations when source schemas change;
- poor asset relationship discovery;
- repeated custom AEM code;
- unsafe or undocumented access to metadata APIs.

SemanticDAM provides a governed internal contract between AEM content, metadata producers, and consumers.

## 3. Goals

### 3.1 Product goals

1. Normalize selected AEM DAM metadata into stable canonical predicates.
2. Preserve the original source, validity, confidence, and value type of every statement.
3. Persist normalized metadata durably in AEM JCR for the first production release.
4. Query an asset’s metadata and discover assets sharing governed facts.
5. Provide a clear extension seam for future SPARQL or external graph storage.
6. Make behavior testable against AEM SDK and understandable to AEM teams.
7. Respect AEM security, service-user, author/publish, dispatcher, and operational conventions.

### 3.2 Success measures

The first release is successful when:

- a metadata change on a DAM asset is indexed once and only once;
- a read-after-write request returns the complete normalized record;
- duplicate predicates and provenance are not lost;
- relationship queries return correct assets for supported predicates;
- unauthorized users cannot retrieve protected metadata;
- the service survives bundle restart and AEM restart without data loss;
- a new predicate can be added through documented ontology and mapping changes;
- the system can be deployed, rolled back, and diagnosed by an AEM operations team.

## 4. Non-goals for the first production release

The following are deliberately excluded from the first production baseline:

- building a general-purpose RDF database;
- implementing a complete OWL reasoner;
- replacing AEM Assets, Content Fragments, or metadata schemas;
- direct bidirectional synchronization with PIM or Workfront;
- AI/ML model hosting or model training;
- a graph visualization authoring application;
- arbitrary user-supplied SPARQL;
- public anonymous access to asset metadata;
- automatic ontology inference without governance approval;
- replacing Adobe’s standard asset search and indexing capabilities.

## 5. Users and stakeholders

### 5.1 DAM author

Needs trustworthy metadata suggestions and asset relationships while working in AEM. Should not need to understand RDF, JCR internals, or source-system schemas.

### 5.2 Metadata steward

Owns vocabulary, field meaning, validation rules, source precedence, and exception handling. Needs explainable provenance and conflict visibility.

### 5.3 AEM developer

Integrates components, workflows, jobs, and APIs with the normalized metadata contract. Needs Java interfaces, examples, test fixtures, and stable response schemas.

### 5.4 Integration developer

Maps inbound PIM, Workfront, or other source data into AEM metadata or a future ingestion adapter. Needs mapping rules, idempotency guidance, and error semantics.

### 5.5 AEM operations and security team

Deploys and monitors the bundle and content packages. Needs service-user mappings, ACLs, dispatcher rules, health signals, logs, rollback guidance, and data migration procedures.

### 5.6 Downstream consumer

Uses normalized asset facts for site rendering, campaign selection, personalization, reporting, or export. Needs a stable API and defined freshness semantics.

## 6. Product principles

1. AEM remains the system of record for the first release.
2. Canonical meaning is governed; source properties are not silently reinterpreted.
3. Every normalized fact carries provenance.
4. Lossless storage is preferred over convenient flattening.
5. Read APIs are least-privilege and safe by default.
6. OOTB AEM, Sling, JCR, QueryBuilder, workflows, and Core Components are preferred over custom platform replacements.
7. Experimental capabilities must be labeled as experimental in code and documentation.
8. A failed indexing operation must be observable and retryable.
9. Schema changes require compatibility and migration consideration.

## 6A. Reproducible implementation brief for coding agents

This section is an executable handoff for an agent rebuilding the repository
from scratch. It is deliberately more precise than a product narrative.

### 6A.1 Build assumptions

- Java 21 and Maven are required.
- The AEM module is `core`; package names start with
  `com.semanticdam.core.core`.
- Domain objects are immutable where practical and reject null/blank required
  values with `IllegalArgumentException`.
- Run focused tests with:

```bash
cd aemassetsmetadata
mvn -B -pl core test
```

- Run the complete package verification with:

```bash
NPM_CONFIG_CACHE=/tmp/aemassetsmetadata-npm-cache \
  npm_config_cache=/tmp/aemassetsmetadata-npm-cache \
  mvn -B package -DskipTests
```

Do not require network model access or a running AI provider for unit tests.

### 6A.2 Required implementation order

1. Implement `AssetId`, `MetadataStatement`, and `AssetMetadata` first.
2. Implement `KnowledgeGraphService` and its in-memory test adapter.
3. Implement source mapping, validation, conflict resolution, and the JCR
   persistence adapter.
4. Add read-only JSON/Turtle endpoints with path validation and no-store
   caching for authenticated metadata responses.
5. Add `GroundedAssetContext`, `AiEnrichmentProvider`, and
   `AiEnrichmentProposal`. Providers may propose only; approval applies facts.
6. Add `AiEnrichmentPolicy`, `AiProposalStore`, and
   `AiProposalReviewService` so proposal limits, predicate allowlists,
   confidence thresholds, decisions, and audit events are testable without a
   model or AEM instance.
7. Add `OpenAiCompatibleEnrichmentProvider` as the first real HTTP adapter;
   require strict JSON output, bounded timeouts, bearer-token configuration,
   and fail-closed behavior. Add `AiEnrichmentProviderRegistry` so provider
   selection is explicit and independent of OSGi ranking.
8. Add `EmbeddingVector`, including required model id/version metadata,
   `EmbeddingProvider`, `VectorSearchIndex`, and `VectorMatch`.
9. Add `InMemoryVectorSearchIndex` using cosine similarity, deterministic
   path tie-breaking, upsert replacement, removal, positive `limit`, finite
   values, and dimension mismatch exclusion.
10. Add `HybridAssetRetriever`: obtain the query embedding, search the vector
   index, add exact graph-filter matches, apply a documented `0.25` graph
   boost, deduplicate by `AssetId`, sort by score descending then path, and
   truncate to `limit`.
11. Add tests for every invariant below before adding external providers.
12. Add `GovernedAiEnrichmentWorkflow` and its AEM JSON actions. `propose`
    persists `PROPOSED` records only; `review` requires an authenticated actor,
    persists the decision, and projects only approved facts into the graph.
    Keep GET read-only and expose proposal state for a steward UI.

### 6A.3 Source-of-truth file map

| Capability | Required files |
|---|---|
| asset identity and facts | `core/.../domain/AssetId.java`, `MetadataStatement.java`, `AssetMetadata.java` |
| graph contract | `core/.../graph/KnowledgeGraphService.java` |
| durable repository | `core/.../graph/JcrKnowledgeGraphStore.java` |
| AI proposal boundary | `core/.../domain/GroundedAssetContext.java`, `AiEnrichmentProposal.java`, `core/.../application/AiEnrichmentProvider.java`, `AiEnrichmentReviewService.java` |
| AI governance | `core/.../application/AiEnrichmentPolicy.java`, `AiProposalStore.java`, `JcrAiProposalStore.java`, `AiProposalReviewService.java`, `AiProposalAuditEvent.java` |
| governed workflow | `core/.../application/GovernedAiEnrichmentWorkflow.java`, `GovernedAiEnrichmentWorkflowComponent.java`, `AiProposalGenerateServlet.java`, `AiProposalQueryServlet.java`, `AiProposalReviewServlet.java` |
| provider integration | `core/.../application/OpenAiCompatibleEnrichmentProvider.java`, `OpenAiCompatibleProviderConfig.java`, `OpenAiCompatibleProviderComponent.java`, `AiEnrichmentProviderRegistry.java` |
| resilience and telemetry | `core/.../application/ResilientAiEnrichmentProvider.java`, `TokenBucketRateLimiter.java`, `SimpleCircuitBreaker.java`, `ProviderTelemetry.java` |
| vector backend | `core/.../application/QdrantVectorSearchIndex.java`, `infra/docker-compose.vector.yml` |
| contextual content variables | `core/.../contextvars/ContextualContentVariableService.java`, `ContextualContentVariableFilter.java`, `PagePropertiesProvider.java` |
| author review UI | `ui.apps/.../components/ai-review/ai-review.html`, `ui.apps/.../clientlibs/clientlib-ai-review/ai-review.js` |
| embedding contract | `core/.../domain/EmbeddingVector.java`, `core/.../application/EmbeddingProvider.java` |
| vector contract | `core/.../application/VectorSearchIndex.java`, `VectorMatch.java` |
| reference retrieval | `core/.../application/InMemoryVectorSearchIndex.java`, `HybridAssetRetriever.java`, `HybridRetrievalResult.java` |
| reproducible verification | `scripts/verify-aem-sdk.sh`, `docs/08-ai-readiness.md` |

### 6A.4 Acceptance tests for the retrieval slice

The implementation is incomplete unless these assertions pass:

- an empty, null, non-finite, or model-less embedding is rejected;
- a vector query with a non-positive limit is rejected;
- cosine-nearest assets rank first;
- equal scores sort by asset path, making results deterministic;
- upsert replaces the prior embedding for the same asset;
- remove prevents an asset from appearing in future results;
- incompatible vector dimensions do not crash a query or produce a match;
- incompatible embedding model ids or versions do not produce a match;
- a graph match and vector match appear once, not twice;
- graph matching adds exactly `0.25` to the reference hybrid score;
- hybrid results sort deterministically and honor the requested limit;
- proposal policy rejects excess, low-confidence, and non-allowlisted facts;
- proposal review persists the decision and audit actor/time/reason without
  applying metadata automatically;
- no test calls an external model, vector database, or network endpoint.
- proposal generation is POST-only and stores no graph fact;
- rejection stores a durable decision and leaves graph metadata unchanged;
- approval stores the decision and writes exactly the approved statement;
- review cannot use an anonymous actor or an empty reason;
- proposal persistence replaces a decision by stable proposal identity rather
  than leaving a stale `PROPOSED` copy visible.

### 6A.5 Production replacement boundary

`InMemoryVectorSearchIndex` is intentionally not production-ready. The
repository includes a Qdrant adapter, but production teams must still provide
TLS, authentication, backups, capacity planning, and availability validation.
Replace or operate the adapter behind `VectorSearchIndex` with durable storage,
tenant/ACL filtering, model and dimension validation, batch upsert, deletion,
re-embedding, index health, timeout behavior, and observability. Keep the
application-level contracts and invariant tests unchanged. A production
embedding provider must likewise remain behind `EmbeddingProvider`, record
model/version/request provenance, enforce payload and timeout limits, and fail
closed without writing authoritative metadata.

### 6A.6 AEM runtime wiring

The live adapters are deliberately not enabled by a checked-in credential
file. Deployments provide the following OSGi configurations through protected
environment configuration:

- `com.semanticdam.core.core.application.OpenAiCompatibleProviderConfig`;
- `com.semanticdam.core.core.application.QdrantVectorIndexConfig`.

Both components use `configurationPolicy=REQUIRE`. The OpenAI-compatible
component also requires `enabled=true`; without it, no provider service can
make a model request. The Qdrant component requires a model id/version and
expects a pre-created collection with the matching vector size and cosine
distance. This is a deployment prerequisite, not a package-install side
effect.

## 7. Information architecture

### 7.1 Core concepts

| Concept | Meaning |
|---|---|
| Asset | An AEM DAM asset identified by its repository path |
| Statement | One governed fact about an asset |
| Predicate | A canonical URI representing the meaning of a field |
| Object value | The value of a fact, either a literal or URI |
| Provenance | The source that supplied or asserted the fact |
| Confidence | A numeric confidence from 0.0 to 1.0 |
| Validity | Whether the statement passed current validation |
| Ontology term | A governed predicate or class with label and optional validation pattern |
| Bundle | A governed asset profile defining allowed predicates |
| Relationship | A link between assets inferred from shared governed statements |

### 7.2 Canonical statement

```text
AssetMetadata {
  assetId: AssetId
  statements: [MetadataStatement]
}

MetadataStatement {
  predicateUri: URI
  objectValue: string
  objectIsUri: boolean
  source: string
  isValid: boolean
  confidenceScore: number [0.0, 1.0]
}
```

The model must retain multiple statements with the same predicate when they differ by value, source, or confidence.

### 7.3 Vocabulary governance

Every production predicate must have:

- a stable URI and namespace owner;
- a human-readable label;
- a description;
- expected datatype or URI/literal semantics;
- cardinality guidance;
- validation rules where applicable;
- permitted source systems;
- source precedence or conflict policy;
- lifecycle status: proposed, active, deprecated, or retired;
- compatibility and migration notes.

The ontology is versioned with the application. Removing or changing predicate meaning requires a migration plan.

## 8. Functional requirements

### FR-1: DAM metadata extraction

The system shall read configured metadata from AEM asset metadata resources under `/content/dam`.

The source adapter shall:

- use a service user rather than administrative credentials;
- support configured property mappings;
- distinguish missing properties from empty values;
- preserve multi-valued source properties;
- identify the AEM asset path in every result.

### FR-2: Canonical mapping

The system shall map source property names to canonical predicate URIs.

Mapping behavior shall be deterministic and covered by tests.

Unknown properties shall be handled according to configuration: ignored, retained as unmapped, or rejected. They shall never be silently mapped to an unrelated predicate.

### FR-3: Validation

The system shall validate statements against the ontology and configured bundle rules.

Validation shall record invalidity and reason where the domain model supports it. Invalid data shall not crash the indexing pipeline.

### FR-4: Provenance and confidence

Every indexed statement shall retain source, validity, and confidence. Defaults must be documented and deterministic.

### FR-5: Conflict resolution

The system shall provide a resolved view based on configured source precedence and validity rules.

The unresolved view shall remain available to authorized callers so stewards can inspect conflicting assertions.

### FR-6: Durable JCR storage

The JCR adapter shall:

- persist data below a dedicated controlled path, default `/var/semanticdam/graph`;
- store each statement losslessly as a child record;
- support duplicate predicates and multi-valued facts;
- preserve URI/literal type, source, validity, and confidence;
- replace a previous asset snapshot idempotently;
- commit atomically per asset;
- use a service user with the minimum required ACLs;
- tolerate bundle restart and AEM restart;
- log failures with asset path and operation context without logging secrets.

### FR-7: Relationship queries

The first release shall support:

- find normalized metadata by asset path;
- find assets matching a predicate/object pair;
- find related assets sharing one or more governed facts;
- exclude the source asset from related results;
- return deterministic ordering and bounded result sets.

Queries shall use approved JCR query APIs and must not accept arbitrary query language from callers.

### FR-8: Indexing trigger

The system shall index changes from the configured DAM change mechanism.

The trigger shall be:

- idempotent;
- bounded to relevant DAM metadata paths;
- protected against self-triggering loops;
- observable through logs and metrics;
- retryable after transient repository or dependency failures.

Bulk ingestion shall have a documented batch or job strategy rather than relying on an unbounded synchronous listener.

### FR-9: Read APIs

The first release shall provide authenticated read endpoints for:

- normalized metadata by asset path;
- related assets;
- predicate/value relationship searches;
- optional resolved versus unresolved metadata views.

Responses shall have a documented versioned schema, UTF-8 encoding, safe JSON escaping, explicit error status codes, bounded results, and no sensitive diagnostic details.

### FR-10: Security

The system shall:

- require authenticated access in production;
- enforce repository permissions through the caller or a deliberately documented service-user model;
- validate asset paths under `/content/dam` at the API boundary;
- reject traversal and malformed paths;
- avoid arbitrary selectors, XPath, SQL2, or SPARQL from requests;
- define author versus publish behavior;
- define dispatcher allowlists and cache behavior;
- avoid caching authorization-dependent responses publicly.

### FR-11: Operations

The system shall expose enough evidence to diagnose:

- bundle activation and unsatisfied references;
- current storage mode and ontology version;
- indexing success/failure counts;
- last indexing error per asset or job;
- query latency and result bounds;
- repository permission failures;
- migration status.

Use AEM/Sling/OSGi health and logging conventions where available.

## 9. API contract

### 9.1 Get metadata

```http
GET /bin/aemassets/metadata.json?path=/content/dam/site/hero.png&resolved=true
```

Successful response:

```json
{
  "assetId": "/content/dam/site/hero.png",
  "resolved": true,
  "ontologyVersion": "1.0.0",
  "statements": [
    {
      "predicate": "https://example.com/aem-assets-ontology#sku",
      "object": "SUM-2026",
      "objectIsUri": false,
      "source": "PIM",
      "isValid": true,
      "confidence": 1.0
    }
  ]
}
```

Errors:

- `400` malformed or missing path;
- `401` unauthenticated;
- `403` unauthorized;
- `404` asset or metadata record not found;
- `409` migration or consistency conflict;
- `429` result or request throttling;
- `500` unexpected repository failure.

### 9.2 Find related assets

```http
GET /bin/aemassets/suggestions.json?path=/content/dam/site/hero.png&limit=20
```

The response shall include the source asset, result count, bounded suggestions, relationship reason where available, and a stable ordering rule.

### 9.3 Find assets by fact

```http
GET /bin/aemassets/related.json?predicate=https%3A%2F%2Fexample.com%2Faem-assets-ontology%23sku&value=SUM-2026&limit=50
```

Only approved predicates may be queried by external callers. The API shall not expose arbitrary repository query syntax.

## 10. AEM architecture

### 10.1 Runtime components

```text
AEM DAM metadata
       |
       v
Metadata source / change trigger
       |
       v
Mapping -> ontology validation -> conflict resolution
       |
       v
KnowledgeGraphService
       |
       +--> JcrKnowledgeGraphStore (production baseline)
       +--> InMemoryKnowledgeGraphStore (tests/local demo)
       +--> SparqlKnowledgeGraphStore (experimental)
       |
       v
Authenticated Sling read APIs / AEM consumers
```

### 10.2 Repository areas

- `/content/dam`: source assets and authored metadata;
- `/var/semanticdam/graph`: normalized graph projection;
- `/apps/semanticdam`: application code, components, clientlibs, and configuration;
- OSGi configuration: storage mode, service user, mappings, ontology version, and operational limits.

The graph is a projection of DAM metadata, not a second authoring surface. Rebuild or reindex procedures must be documented.

### 10.3 Service users and ACLs

The indexing service user requires only the minimum read access to configured DAM metadata and write access below the graph root. API access must be evaluated separately from indexing access.

Do not grant administrative access. Do not embed credentials in code, content packages, or logs.

## 11. Non-functional requirements

### Performance

- single-asset read p95 target: under 250 ms in a representative local deployment;
- relationship query p95 target: under 750 ms for the agreed dataset and result limit;
- indexing operation shall have a bounded timeout;
- all list APIs shall enforce a maximum result size;
- performance tests shall use realistic DAM metadata cardinality.

### Reliability

- no silent data loss on indexing failure;
- idempotent retries;
- repository writes atomic per asset;
- bundle restart does not invalidate stored graph data;
- deployment rollback does not require destructive repository cleanup.

### Security

- least-privilege service users;
- authenticated API access;
- authorization-aware caching;
- safe query construction;
- no arbitrary query execution;
- auditability of administrative changes.

### Compatibility

- supported AEM SDK and Java version documented in the root build;
- author and publish behavior tested separately where behavior differs;
- ontology and storage schema versioned;
- API response changes are backward-compatible or versioned.

### Operability

- structured logs with correlation/asset identifiers;
- health and activation evidence;
- deployment and rollback runbooks;
- measurable indexing and query outcomes;
- actionable error messages without secrets.

## 12. Testing strategy

### Unit tests

Cover domain validation, mapping, ontology lookup, conflict resolution, JSON escaping, query construction, and in-memory graph behavior.

### AEM mock tests

Cover Sling Models, servlet status codes, resource path validation, metadata extraction, and service-user interactions where mockable.

### AEM SDK integration tests

Against a real local AEM SDK, verify:

1. package installation;
2. bundle activation and OSGi references;
3. service-user mapping and ACLs;
4. authored DAM asset metadata;
5. indexing trigger;
6. JCR graph write;
7. read-after-write API behavior;
8. relationship query behavior;
9. author/publish access rules;
10. restart persistence;
11. rollback or migration behavior.

### Security tests

Test unauthenticated, unauthorized, traversal, malformed predicate, oversized result, and cache behavior. Verify that arbitrary query syntax cannot reach the repository.

### Performance tests

Use representative asset counts, statement counts, duplicate predicates, and relationship fan-out. Record p50/p95/p99 latency and repository query plans where possible.

## 13. Deployment and rollout

### Pre-deployment checklist

- supported AEM SDK/Cloud Manager build confirmed;
- service user and mappings provisioned;
- ACLs reviewed;
- graph root and repository permissions verified;
- dispatcher rules reviewed;
- ontology version recorded;
- reindex and rollback plan approved;
- monitoring and alerting configured;
- integration and security tests green.

### Rollout

1. Install code and OSGi configuration with indexing disabled or in observe-only mode.
2. Validate bundle activation and permissions.
3. Run a bounded backfill against a representative DAM folder.
4. Compare source metadata, normalized records, and API results.
5. Enable change-triggered indexing.
6. Monitor failures, latency, and repository growth.
7. Expand scope gradually.

### Rollback

Rollback must remove or disable code without deleting the graph projection. A later compatible version must be able to read the stored schema. Destructive graph cleanup requires a separately approved migration.

## 14. Acceptance criteria for production baseline

The first production release is accepted only when all are true:

- [ ] 100% of supported unit and AEM mock tests pass.
- [ ] AEM SDK package-install test passes.
- [ ] Core bundle activates with no unsatisfied references.
- [ ] Service-user and ACL tests pass.
- [ ] A DAM metadata change produces exactly one current graph snapshot.
- [ ] Duplicate predicates, URI objects, provenance, validity, and confidence round-trip correctly.
- [ ] Relationship queries are correct, bounded, deterministic, and tested on realistic data.
- [ ] API authorization and path-validation tests pass.
- [ ] JSON responses remain valid for quotes, slashes, control characters, Unicode, and null-like values.
- [ ] Dispatcher and cache behavior is approved.
- [ ] Restart persistence test passes.
- [ ] Failure, retry, and rollback runbooks exist.
- [ ] Logs and metrics identify indexing/query failures without exposing secrets.
- [ ] README and operations documentation match verified behavior.

## 15. Future roadmap

### Phase 2: enterprise ingestion

- authenticated PIM and Workfront adapters;
- asynchronous jobs and retries;
- reconciliation and dead-letter handling;
- source-specific ownership and freshness metadata.

### Phase 3: external graph scale

- complete SPARQL result parsing;
- tested triplestore compatibility matrix;
- bounded remote relationship queries;
- dual-write or migration strategy;
- consistency and outage behavior.

### Phase 4: author experience

- explainable relationship UI;
- metadata stewardship workflow;
- graph exploration with accessibility and permission filtering;
- author feedback and correction loops.

### Phase 5: intelligence

- governed AI enrichment;
- confidence calibration;
- human approval workflows;
- measurable recommendation quality and drift monitoring.

## 16. Key risks and mitigations

| Risk | Mitigation |
|---|---|
| Graph projection diverges from DAM | idempotent rebuild, versioned schema, reconciliation job |
| Repository growth affects AEM | bounded retention, indexing strategy, performance tests |
| Incorrect ontology mapping | steward approval, test fixtures, versioned vocabulary |
| Unauthorized metadata exposure | least privilege, endpoint authorization, dispatcher review |
| Listener storms during bulk updates | Sling Jobs/batching, debounce, backpressure |
| Conflicting source values | explicit provenance and deterministic precedence |
| External graph outage | JCR remains source of record for baseline; controlled fallback |
| AI recommendations are untrustworthy | human review, confidence thresholds, quality metrics |

## 17. Definition of done

The product is done for a release when the implementation, tests, documentation, deployment package, security review, operational runbook, and rollback plan all describe the same behavior. A feature is not considered complete merely because its Java class or endpoint exists.
