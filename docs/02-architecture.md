# Architecture

SemanticDAM turns flat AEM Assets metadata into a governed knowledge graph and serves it as
context to authors, applications and AI agents. It is one OSGi bundle for AEM as a Cloud Service.
The core has no third-party runtime dependencies; vector search and an external triplestore are
optional adapters.

This page covers the system context, the components, the data model, the two main flows (ingest
and read), how failures are handled, the security model, performance, and how to extend it. For
the API surface see [11-context-engine.md](11-context-engine.md); for the reasons behind the
design see [05-decisions.md](05-decisions.md); for why it exists next to Adobe's own AI features see
[12-positioning-and-moat.md](12-positioning-and-moat.md).

## 1. System context

```
  PIM, Workfront, DAM authors,                 Ontology owner
  AEM tags, metadata schemas                   (information architect)
            |                                          |
            | write jcr:content/metadata               | Turtle: classes, mappings,
            v                                          v shapes, taxonomy, rules
  +-----------------------------------------------------------------------+
  |  AEM author                                                           |
  |                                                                       |
  |   AEM Assets  ---- change events ---->  SemanticDAM bundle            |
  |   /content/dam                           |  graph in /var/semanticdam |
  |   /content/cq:tags                       |  proposals + audit trail   |
  +------------------------------------------|----------------------------+
                                             |
        +-------------------+----------------+---------+-------------------+
        |                   |                          |                   |
   HTTP JSON / Turtle   MCP (agents)        CCVAR tokens, JSON-LD    OSGi event READY
   apps, integrations   Claude, IDEs        in authored pages        (e.g. to AJO)

  Vocabulary steward (group semanticdam-vocabulary-stewards): approves, rejects or merges
  candidate entries through /bin/aemassets/vocabulary.

  Optional, outside AEM:
   - OpenAI-compatible embeddings endpoint + Qdrant   (semantic search)
   - OpenAI-compatible chat endpoint                  (AI fact proposals)
   - SPARQL 1.1 triplestore                           (graph storage instead of JCR)
```

Everything runs on author. Nothing in the bundle is required on publish, and the
`/bin/aemassets` paths stay blocked by the publish dispatcher.

## 2. Components

```
                         +---------------------------+
   DAM change ---------> | AssetMetadataResource-    |  observation thread: map paths to
                         | ChangeListener            |  assets, queue one job per asset
                         +-------------+-------------+
                                       | Sling job  com/semanticdam/asset/ingest
                                       v
 +----------------------+   +----------------------------+   +----------------------+
 | SlingResourceAem-    |-->| DefaultAssetMetadataPipeline|-->| AssetMetadataIndexing |
 | MetadataSource       |   |  map -> merge -> infer ->   |   | Service               |
 | JcrTagResolver       |   |  validate -> write -> events|   +----------+-----------+
 +----------------------+   +-----+-------------+--------+              |
                                  |             |                       v
              +-------------------+--+   +------+-----------+   +-----------------------+
              | AemAssetMetadataMapper|   | InferenceService |   | KnowledgeGraphService |
              | MetadataValidator     |   | SemanticEvent-   |   |  JCR | SPARQL | memory|
              | (SHACL)               |   | Service          |   +-----------+-----------+
              +-----------+-----------+   +------------------+               ^
                          |  all read the ontology                           |
                          v                                                  |
              +-----------------------+                                      |
              | OntologyRegistry-     |  bundled asset-ontology.ttl          |
              | Component             |  + extension files from /conf        |
              +-----------------------+                                      |
                                                                             |
 Read side                                                                   |
 +-----------------+   +---------------------+   +----------------------+   |
 | Servlets, MCP   |-->| AssetContextService |-->| GraphTraversalService|---+
 | (caller's ACL)  |   |  + ConflictResolution|  | SemanticSearchService|--> embeddings, Qdrant
 +-----------------+   +---------------------+   +----------------------+

 AI side
 AiProposalWorkflowProcess / propose endpoint -> GovernedAiEnrichmentWorkflow
     -> AiEnrichmentProvider (proposals) -> JcrAiProposalStore (proposals + audit)
     -> review endpoint (human) -> graph write -> pipeline re-ingest
```

| Package | Responsibility | Depends on AEM? |
|---|---|---|
| `rdf` | RDF terms, triples, indexed graph, Turtle parser | No |
| `ontology` | `OntologyModel`, `ShaclValidator`, `Vocabulary`, the registry component | Only the OSGi component |
| `domain` | `AssetId`, `AssetMetadata`, `MetadataStatement`, `Entity`, `AiEnrichmentProposal` | No |
| `graph` | `KnowledgeGraphService` port; JCR, SPARQL and in-memory adapters; Turtle export | Only the JCR adapter |
| `application` | Pipeline, mapping, inference, validation, conflict resolution, traversal, context, search, AI workflow, servlets | Yes |
| `contextvars` | `((semantic_properties.x))` tokens in authored text | Yes |

Most behaviour lives in classes that need no AEM runtime, which is why 131 unit tests cover it
without a running instance.

## 3. Data model

### 3.0 Ontology and application profile

The model is two Turtle files, following the controlled vocabulary -> taxonomy -> thesaurus ->
ontology -> graph progression:

| File | Contains | Changes |
|---|---|---|
| `asset-ontology.ttl` (domain ontology, 3.0.0) | Classes, properties, PROV agents, the bundled taxonomy with schemes, alternative and hidden labels; every term has a `skos:definition` (enforced by a test) | Rarely |
| `application-profile.ttl` (profile-1) | Source trust, DAM field mappings and their vocabulary policy, SHACL shapes and bundles, readiness contracts, profile-maintained vocabulary entries, inference rules | Per project; extend with files under `/conf` |

### 3.1 Logical model

```
 Asset --dcterms:title----------> "Powder day"
   |   (prov:wasAttributedTo agent:AEM, confidence 0.75, valid)
   +---schema:brand-------------> Brand "Summit"            status APPROVED | CANDIDATE | REJECTED | MERGED
   +---dcterms:subject----------> Concept "Italy"            (most specific concept only)
   |                                 skos:inScheme  scheme "WKND Shared"   (the AEM tag namespace)
   |                                 skos:broader   "EMEA" -> "Region"
   +---sdam:broaderSubject------> "EMEA", "Region"           (derived, for retrieval; never asserted)
   +---rdf:type-----------------> sdam:ProductImage
```

- A **statement** is predicate + object + provenance: the PROV agent (`agent:PIM`, `agent:Workfront`,
  `agent:AEM`, `agent:Inference-Engine`, `agent:AI%3A<model>`), confidence (the agent's trust in
  the profile) and validity (SHACL plus vocabulary status).
- An **entity** is a controlled-vocabulary entry: brand, product, campaign, concept, AEM tag or
  concept scheme, with preferred, alternative and hidden labels, broader concepts, scheme and a
  status. Only APPROVED entries make a link valid.
- **Indexing specificity:** assets are indexed at the most specific concept (`dcterms:subject`).
  Ancestors are derived into `sdam:broaderSubject`, which the ontology declares as expanding
  `dcterms:subject`, so a search for EMEA finds assets indexed at Italy without indexing them at EMEA.
- The same fact from two sources is two statements. Conflict resolution produces an
  **authoritative view** on read.

IRIs:

| Node | IRI |
|---|---|
| Asset | `https://semanticdam.com/id/asset<DAM path>` |
| Brand, product, campaign, concept | `https://semanticdam.com/id/<kind>/<slug>` (from the class's `sdam:instanceNamespace`) |
| AEM tag / tag namespace (scheme) | `https://semanticdam.com/id/tag/<namespace>/<path>` / `https://semanticdam.com/id/tag/<namespace>` |
| Agent | `https://semanticdam.com/id/agent/<label>` |
| Concept declared in the ontology | its own IRI, e.g. `sustain:OrganicCotton` |

### 3.2 JCR layout (default storage)

```
/var/semanticdam/graph/content/dam/site/hero.jpg            nt:unstructured
    statements/statement-00001                              sling:resourceType=semanticdam/graph/statement
        predicate, object, objectIsUri, source, confidence, valid
/var/semanticdam/entities/<2 hex>/<sha-256 of IRI>          uri, type, label, status, broader[], altLabels[],
                                                            hiddenLabels[], scheme, replacedBy
/var/semanticdam/proposals/content/dam/site/hero.jpg
    items/proposal-<hash>                                   statement + modelId, promptVersion, rationale, status
    audit/event-<millis>                                    status, actor, occurredAt, reason, model
```

Statement lookups use the Lucene index `semanticdam.statement-custom-1` and the steward queue
(entities by status and class) uses `semanticdam.entity-custom-1`, both selected with
`OPTION(INDEX TAG semanticdam)`. Verified on the local SDK.

Governed facts are also written back to the asset as `semanticdam:*` properties on
`jcr:content/metadata` (labels, not IRIs; `semanticdam:status`; `semanticdam:ontologyVersion`), so
they replicate with the asset and are visible to AEM search and to pages.

### 3.3 SPARQL layout (alternative storage)

Each statement is written twice: as a plain triple (`<asset> <p> <o>`) so that graph tools see
ordinary RDF, and as an `rdf:Statement` carrying `prov:wasAttributedTo`, `sdam:confidence`,
`sdam:valid`, `sdam:assetPath` and `sdam:position`, so that reads round-trip exactly. Entities use
SKOS (`prefLabel`, `altLabel`, `hiddenLabel`, `broader`, `inScheme`) plus `sdam:vocabularyStatus`
and `dcterms:isReplacedBy`. The Turtle export uses the same shapes.

## 4. Ingest flow

```
 metadata saved
   -> listener (observation thread, no repository work)
        reduces the batch to one action per asset: ingest | remove
   -> Sling job "com/semanticdam/asset/ingest"   (4 parallel, 5 retries)
   -> pipeline
        1 read DAM properties                        asset gone -> remove from graph, stop
        2 resolve AEM tags (TagManager)
        3 map with the profile's PropertyMappings      literals, entity links, classes
          entity values resolve: profile label (pref/alt/hidden) -> stored entry (following
          merges) -> new entry, APPROVED if the source is the authority, else CANDIDATE
        4 merge: keep stored statements whose source the pipeline does not own (AI:*)
        5 validate statements (SHACL)
        6 infer: broader concepts (into sdam:broaderSubject) + rules   only from valid statements
        7 validate again (inferred facts too)
        8 write changed entities, then the asset     one commit per asset
        9 READY event if a readiness shape newly conforms
       10 write governed facts back to the asset's metadata (semanticdam:*), if changed
   -> if semantic search is configured: job "com/semanticdam/asset/embed" (2 parallel, 10 retries)
        text from valid facts -> embedding -> Qdrant upsert
```

Ownership rule: the pipeline rebuilds statements from ontology source systems and from
`Inference-Engine`; it never touches others. That is what keeps reviewed AI facts alive across
metadata edits, and why inferred facts disappear when their cause does.

## 5. Read flow

```
 request (authenticated AEM user)
   -> servlet / MCP tool
        path must be below /content/dam
        caller's own resolver must see the asset            else 404, as if it did not exist
   -> AssetContextService
        graph read (service user)
        authoritative view (ConflictResolution)
        violations (SHACL, on the authoritative view)
        readiness (readiness shapes)
        related assets (GraphTraversalService) -> each filtered by the caller's access
   -> JSON / Turtle / MCP result
```

Related assets are ranked by summing, over the shared entities, `1 / ln(1 + n)` where `n` is the
number of assets linked to that entity. Only asserted, valid links count: derived broader subjects
never make two assets related.

Conflict resolution for a single-valued predicate (precedence rule or `sh:maxCount 1`): a valid
value beats an invalid one; then an explicit precedence rule; otherwise the most trusted source.
Multi-valued predicates keep every distinct value.

## 6. Controlled vocabulary and stewardship

| Source of a new value | Policy (profile) | Result |
|---|---|---|
| Authority for that vocabulary (PIM for products, Workfront for campaigns, AEM tags) | `sdam:Approved` | New entry approved |
| Not the authority (author-typed brand, PIM free-text category) | `sdam:Candidate` | New entry is a candidate; links to it are invalid with a readable violation |

A steward lists candidates with their usage (`GET /bin/aemassets/vocabulary/candidates.json`) and
decides (`POST /bin/aemassets/vocabulary/review`): approve, reject, or merge into an existing entry.
A merge marks the candidate MERGED with `dcterms:isReplacedBy`, adds its label as a hidden label of
the target, and future values with that spelling resolve to the target. Each decision re-ingests
the assets that use the entry.

## 7. Caching on publish

Pages show facts through `((semantic_properties.*))`, read from the asset's written-back metadata.
Facts on publish change only when the asset is republished, and AEM's reverse-reference
invalidation does not cover DAM assets. `PageCacheInvalidation` (author only) listens for
distribution of an asset to publish, finds the pages whose own `jcr:content` names it
(`semanticdam:asset` or the featured image) with AEM's `ReferenceSearch`, and sends one deep
`INVALIDATE` request through Sling Content Distribution. Full-page caching stays on; only the
affected pages are invalidated. The CDN copy expires with its HTML TTL. Sling Dynamic Include (SSI)
also works with the CCVAR filter, for components that must be fresh on every request; see
[06-operations.md](06-operations.md#caching-pages-that-show-governed-facts).

## 8. Failure handling

| Failure | Behaviour |
|---|---|
| Repository or graph store unavailable during ingest | Exception, job returns FAILED, Sling retries; the stored graph is untouched |
| Service user cannot log in | Same as above; never treated as "asset has no metadata" |
| Graph store unavailable during a request | HTTP 503 / MCP `isError` |
| Ontology extension file invalid | Rejected as a whole; previous ontology stays active; error logged |
| Embedding endpoint or Qdrant down | Embed job retried 10 times, 30 s apart; graph and ingest unaffected |
| AI provider down | Propose call fails with 409; circuit breaker and rate limit in front of the provider |
| AEM tag deleted but still on an asset | Kept as a concept labelled with the tag ID, no parent |
| Asset deleted | Removal job deletes the graph node and its vector |
| Folder moved | Not detected per asset; run the reindex endpoint on the target |
| Value not in a controlled vocabulary | Kept, linked to a candidate entry, flagged invalid until a steward decides |
| Publish agent unavailable during invalidation | Job retried by Sling; page paths logged |

## 9. Security model

- The graph, entities and proposals live under `/var/semanticdam`, readable only by the service
  user `aem-assets-metadata-svc` (sub-service `semanticdam`) and administrators.
- Every endpoint and MCP tool checks the caller's own read access to each asset it returns. This
  includes related assets, search hits and every asset on a relationship path.
- Proposing and reviewing AI facts requires permission to modify the asset's metadata; the
  reviewer's user id is stored in the audit trail. Reindexing requires write access to the graph root.
- The source of an AI statement is set by the server (`AI:<model>`), never by model output.
- JSON-LD output escapes `<`, `>` and `&`; Turtle output escapes literals and IRIs.
- The MCP endpoint rejects cross-origin requests, takes one JSON-RPC message of at most 64 KB, and
  exposes read-only tools.
- Secrets (AI, embeddings, Qdrant, SPARQL) are password-type OSGi properties meant to be supplied
  as Cloud Manager secret variables.

## 10. Performance

Measured on the local AEM SDK 2026.7 author (laptop), 2026-11-17:

| Operation | Result |
|---|---|
| Backfill of 220 WKND assets | about 2 s, 4 parallel jobs |
| Metadata edit to updated graph | 1-2 s |
| Embedding 187 assets with Ollama `mxbai-embed-large` | about 25 s |
| Fact lookup query | index plan on `semanticdam.statement-custom-1`, no traversal |

Cost model: one JCR node per statement; a related-assets request issues one indexed query per
entity the asset links to, then reads each candidate asset. Path finding is breadth-first and
stops after 500 expanded nodes. Not measured: tens of thousands of assets, and entities linked to
very many assets (a related-assets request reads every asset that shares an entity).

## 11. Extending

| To | Do |
|---|---|
| Map a new DAM field | Add an `sdam:PropertyMapping` (with an `sdam:entityPolicy` for entity values) in an extension Turtle file, reindex |
| Seed a controlled vocabulary | Declare entries with `skos:prefLabel`, `altLabel`, `hiddenLabel` and `skos:definition` in an extension file |
| Add a class, bundle or validation | Add a class and a SHACL shape (closed for a bundle) |
| Add taxonomy | Add `skos:Concept`s with `skos:broader`, or just use AEM tags |
| Add an inference | Add an `sdam:InferenceRule` |
| Add a readiness signal | Add a shape typed `sdam:ReadinessShape`; handle the READY event |
| Use another graph store | Implement `KnowledgeGraphService`, select it in `GraphStorageConfig` |
| Use another AI or embedding model | Implement `AiEnrichmentProvider` / `EmbeddingProvider`, or point the OpenAI-compatible ones elsewhere |

None of these require Java changes except the last two.
