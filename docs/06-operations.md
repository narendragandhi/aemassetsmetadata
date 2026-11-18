# Operations guide

## What gets installed

| Item | Where | Purpose |
|---|---|---|
| Service user `aem-assets-metadata-svc` | repoinit | Reads `/content/dam`, `/content/cq:tags` and ontology extensions; reads and writes `/var/semanticdam` |
| Service user mapping | `semanticdam.core:semanticdam` | Every service login in the bundle uses the sub-service `semanticdam` |
| `/var/semanticdam/graph`, `/entities`, `/proposals` | repoinit | Graph, entity nodes, AI proposals and audit trail |
| Oak index `semanticdam.statement-custom-1` | `ui.apps` | Statement lookups. Without it queries traverse |
| Job queue `SemanticDAM asset ingest` | `ui.config` | 4 parallel jobs, 5 retries, 5 s apart |
| Job queue `SemanticDAM asset embedding` | `ui.config` | 2 parallel jobs, 10 retries, 30 s apart |
| JCR namespace `semanticdam` | repoinit | For `semanticdam:assetType` and the written-back `semanticdam:*` facts |
| Oak index `semanticdam.entity-custom-1` | `ui.apps` | Steward queue: entities by status and class |
| Group `semanticdam-vocabulary-stewards` | repoinit | Who may approve, reject and merge vocabulary entries |
| Service user `semanticdam-invalidation-svc` | repoinit | Reads pages and sends dispatcher invalidation (`crx:replicate` on `/content`); author only |
| Write-back ACL | repoinit | Main service user may modify only `semanticdam:*` properties of asset metadata |

## Configuration

| PID | Setting |
|---|---|
| `com.semanticdam.core.core.graph.GraphStorageConfig` | `mode`: `jcr` (shipped default), `in-memory`, `sparql` |
| `com.semanticdam.core.core.ontology.OntologyRegistryComponent` | `extensionPaths`: Turtle files merged into the core ontology |
| `com.semanticdam.core.core.application.ConflictResolutionServiceComponent` | Source precedence per predicate |
| `com.semanticdam.core.core.application.OpenAiCompatibleProviderComponent` | AI provider; inactive without configuration |
| `com.semanticdam.core.core.graph.SparqlEndpointConfig` | Query/update endpoints, credentials and named graph for `sparql` mode |
| `com.semanticdam.core.core.application.OpenAiCompatibleEmbeddingProviderComponent` | Embeddings endpoint and model for semantic search |
| `com.semanticdam.core.core.application.QdrantVectorIndexComponent` | Qdrant URL, collection and model for semantic search |

The log line `Graph storage mode '<x>' is unknown or not available; using in-memory storage`
means the graph is not being persisted.

Never commit the AI provider's `apiKey` or the SPARQL password. Supply them as Cloud Manager
secret environment variables (`$[secret:NAME]`).

## Extending the ontology

1. Write a Turtle file with your additions: properties, `sdam:PropertyMapping`s for your DAM
   fields, shapes, concepts, rules. Use `asset-ontology.ttl` as the reference for each construct.
2. Store it as an `nt:file` below `/conf/semanticdam/settings/ontology` (the service user can read
   that folder).
3. Add its path to `extensionPaths`.
4. Check the log for `Loaded ontology <version>: n terms, n mappings, n rules`. If the file is
   invalid the log says so and the previous ontology stays active.
5. Reindex so existing assets are mapped and validated against the new model.

The validator supports a subset of SHACL Core, listed in `ShaclValidator`. A constraint outside
that subset is ignored.

## Indexing

- **Automatic:** a change to an asset's `jcr:content/metadata` node, an upload, a move of a single
  asset, or a delete queues a job.
- **Backfill / after ontology changes / after moving a folder:**

  ```bash
  curl -u <admin> -X POST "$AEM/bin/aemassets/reindex?path=/content/dam/brand-x&limit=5000"
  ```

  The response gives `queued` and `truncated`. When truncated, call it again per sub-folder. The
  caller needs write access to `/var/semanticdam/graph`.
- **Monitoring:** job statistics for the topic `com/semanticdam/asset/ingest` are in the Sling
  Jobs console. A job that failed five times is logged as
  `Ingest of <path> failed (attempt n); it will be retried` and then dropped by Sling; reindex the
  path once the cause is fixed.

## Endpoints

All endpoints are listed in [11-context-engine.md](11-context-engine.md#asking-for-context). They
require an authenticated AEM user and return 404 for assets that user cannot read. `503` means the
graph store could not be reached.

They are intended for author. The archetype's dispatcher filters deny `/bin` on publish; keep it
that way unless you have reviewed caching (responses are `no-store`) and load.

## AI enrichment

The AI endpoints and the workflow step `SemanticDAM: Propose AI enrichment for review` are active
only while a provider is configured with `enabled=true`, an endpoint, a model and the list of
`allowedPredicates`. With an empty list every proposal is rejected by policy.

Proposing and reviewing require permission to modify the asset's metadata. Each decision is stored
under `/var/semanticdam/proposals/<asset path>/audit` with reviewer, time, reason and model, and is
returned by `GET /bin/aemassets/ai/proposals.json`.

## Events

`com/semanticdam/asset/READY` (OSGi EventAdmin) is posted when an asset first conforms to a
readiness shape. Properties: `path`, `consumer`, `shape`. Register an `EventHandler` to forward it,
for example to Adobe I/O Events; nothing is forwarded out of the box.

## Semantic search

Configure both components with the **same** `modelId` and `modelVersion`:

```text
OpenAiCompatibleEmbeddingProviderComponent
  endpoint      https://<gateway>/v1/embeddings   (local: http://localhost:11434/v1/embeddings)
  apiKey        $[secret:EMBEDDINGS_API_KEY]
  modelId       text-embedding-3-small            (local: mxbai-embed-large)
  modelVersion  1
QdrantVectorIndexComponent
  baseUrl       https://<qdrant>:6333             (local: http://localhost:6333)
  apiKey        $[secret:QDRANT_API_KEY]
  collection    semanticdam
  modelId / modelVersion   as above
```

The collection is created on first use with the embedding's dimension and cosine distance. Then
reindex (`/bin/aemassets/reindex`) to embed existing assets. To change the embedding model, change
both versions, use a new collection, and reindex. `infra/docker-compose.vector.yml` starts a local
Qdrant; harden it (TLS, API key, persistence, backups) before production use.

## Vocabulary stewardship

Add stewards to `semanticdam-vocabulary-stewards`. They work with:

```bash
curl -u <steward> "$AEM/bin/aemassets/vocabulary/candidates.json?class=Brand"
curl -u <steward> -X POST "$AEM/bin/aemassets/vocabulary/review" \
  --data-urlencode "entity=https://semanticdam.com/id/brand/summit" \
  --data-urlencode "decision=approve" --data-urlencode "reason=official brand"
curl -u <steward> -X POST "$AEM/bin/aemassets/vocabulary/review" \
  --data-urlencode "entity=https://semanticdam.com/id/brand/sumit" --data-urlencode "decision=merge" \
  --data-urlencode "target=https://semanticdam.com/id/brand/summit" --data-urlencode "reason=typo"
```

To pre-approve a vocabulary, declare its entries in a profile extension (preferred label,
alternative and hidden labels, definition). To make a source authoritative for a vocabulary, set
`sdam:entityPolicy sdam:Approved` on its mapping.

## Caching pages that show governed facts

Default (recommended): **targeted invalidation**. `PageCacheInvalidation` is configured in
`config.author` and needs nothing else on AEM as a Cloud Service: when an asset reaches publish,
the pages that name it (`semanticdam:asset` or featured image) are invalidated through Sling Content
Distribution (`INVALIDATE`, deep). After bulk changes, queue it manually:
`POST /bin/aemassets/invalidate?asset=/content/dam/...` (requires replicate permission). The
Adobe-managed CDN keeps its copy until its HTML TTL expires.

Alternative: **Sling Dynamic Include** (SSI) for components that must be fresh on every request.
Adobe documents that `mod_include` is loaded by default on the AEM as a Cloud Service dispatcher and
that SDI with SSI works there (ESI does not work with the Adobe CDN). Embed the
`org.apache.sling.dynamic-include` bundle, configure it with include type SSI for the component's
resource type, and do not cache the `nocache` selector. The CCVAR filter resolves tokens in the
fragment request because the provider walks up from the component to its page. The CDN still caches
the assembled page, so SDI does not make the CDN fresher than its TTL.

Not recommended: auto-republishing referencing pages (publishes unrelated drafts) and broad flush
rules (throw away the site's cache).

## Upgrading from the earlier prototype

Earlier builds never bound the storage configuration and ran in memory, so there is normally no
persisted graph to migrate (confirmed on the local SDK: `/var/semanticdam/graph` was empty). If `/var/semanticdam/graph` does contain data from an earlier build,
delete it and reindex: predicates and entity links have changed.

From ontology 2.x to 3.0: entities stored before vocabulary statuses existed are read as APPROVED.
To put unreviewed brands and categories through steward review, delete the children of
`/var/semanticdam/entities` and reindex. Assets indexed with ancestor subjects are corrected by a
reindex (ancestors move to `sdam:broaderSubject`).

## Build target

`aem.sdk.api` in the root `pom.xml` must not be newer than the AEM runtime you deploy to: the
bundle's import ranges follow the compile-time API. Compiling against SDK 2026.10 produced a bundle
that required Gson 2.14 and SLF4J 2.0 and did not resolve on a 2026.7 SDK. The build currently
targets 2026.7.27083; Cloud Service environments are always at or above it.
