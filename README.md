# SemanticDAM - a governed knowledge layer for AEM Assets

SemanticDAM is an accelerator for AEM Assets. It turns the flat metadata on assets into a governed
knowledge graph and serves it as context to authors, downstream applications and AI agents. It
complements Adobe's AI Search, GenAI metadata, Content Discovery agent and AEM MCP Server: those
find and act on assets, SemanticDAM makes the facts underneath them consistent, validated,
explainable and connected. See [positioning](docs/12-positioning-and-moat.md).

For every asset it can answer: what is known, where each fact came from and how much it is trusted,
whether it conforms to the model, which brands, products, campaigns and categories it is linked to,
which other assets it is related to and why, and what it is ready for.

It is one OSGi bundle for AEM as a Cloud Service with no third-party runtime dependencies.

## What it does

- **Ontology-driven.** A domain ontology (classes, properties, taxonomy; every term defined) and an
  application profile (mappings, trust, SHACL shapes, rules). Nothing about individual fields is
  hardcoded in Java, and projects extend the profile with their own Turtle files instead of forking.
- **Controlled vocabularies.** Brands, products, campaigns and categories resolve against governed
  entries through preferred, alternative and hidden labels. Values from a non-authoritative source
  become candidates that a vocabulary steward approves, rejects or merges.
- **A real graph.** Brands, products, campaigns, categories and AEM tags are entity nodes, with the
  tag hierarchy as `skos:broader`. Assets are related through shared entities, ranked by how rare
  the shared entity is, with multi-hop path finding.
- **Provenance on every fact.** Source, confidence and validity per statement; conflict resolution
  yields an authoritative view without discarding the raw values.
- **Validation.** SHACL shapes flag invalid values and define closed "bundles" such as
  `ProductImage`. Invalid facts are kept and flagged, never silently dropped.
- **Inference.** `skos:broader` closure and declarative if/then rules, recomputed on every ingest.
- **Governed AI enrichment.** A provider proposes, a person approves, an append-only audit trail
  records it. Model output cannot claim a trusted source, and approved facts survive re-ingest.
- **Semantic grounding for agents (optional).** Assets are embedded from their governed facts so an
  agent can search by meaning and get back facts with provenance. For end-user search, AEM's AI
  Search remains the primary experience.
- **Pluggable storage.** JCR by default, or any SPARQL 1.1 triplestore with full read/write.
- **Governed facts on pages.** `((semantic_properties.brand))` in Sites pages, from facts written
  back to the asset; full-page caching kept with targeted dispatcher invalidation.
- **Access-controlled delivery.** HTTP endpoints, an MCP server, contextual content variables and
  JSON-LD, all filtered by the caller's own read access.

Start with [docs/12-positioning-and-moat.md](docs/12-positioning-and-moat.md) for how it fits with
Adobe's features, [docs/02-architecture.md](docs/02-architecture.md) for how it is built, and
[docs/11-context-engine.md](docs/11-context-engine.md) for the API and real sample output.

## Quick start

Requires Java 21 and Maven 3.9.

```bash
# build and test everything, including package validation and the AEM analyser
CYPRESS_INSTALL_BINARY=0 MAVEN_OPTS="-Xmx4g" mvn clean install

# core tests only
mvn -B -pl core test

# deploy to a local AEM SDK author on :4502 (SDK 2026.7 or newer)
mvn clean install -PautoInstallSinglePackage

# check the deployment end to end
./scripts/verify-aem-sdk.sh
```

Then ask for context:

```bash
curl -u admin:admin "http://localhost:4502/bin/aemassets/context.json?path=/content/dam/semanticdam/asset.jpg"
```

To see the whole story in one go (architecture, governed facts, vocabulary stewardship, taxonomy,
relationships, page facts, agents, value by audience):

```bash
bash demo/run-demo.sh          # talk track: docs/07-show-and-tell.md
```

Existing assets are not indexed until their metadata changes. To backfill:

```bash
curl -u admin:admin -X POST "http://localhost:4502/bin/aemassets/reindex?path=/content/dam&limit=5000"
```

## Project structure

| Module | Contents |
|---|---|
| `core` | The bundle: ontology, graph, pipeline, API. See [architecture](docs/02-architecture.md#packages) |
| `ui.apps` | The AI review component and the Oak index for graph lookups |
| `ui.config` | OSGi configuration: storage mode, service user, repoinit, job queue |
| `ui.content` | A demo asset |
| `it.tests`, `ui.tests`, `dispatcher` | Archetype defaults |

## Verification status

| Checked | How |
|---|---|
| Turtle parser reads the ontology exactly as Apache Jena does | unit test, every build |
| SHACL validator reports the same violations as Jena SHACL | unit test, every build |
| SPARQL store queries and updates are valid SPARQL with exact round-trips | unit tests against Jena's in-memory SPARQL engine |
| Pipeline, vocabularies and stewardship, tags and schemes, inference, merge, conflict resolution, traversal, context API, MCP, semantic search, CCVAR, write-back, access filtering | 145 unit tests |
| Bundle imports, repoinit and packages valid for AEM as a Cloud Service | AEM analyser and FileVault validation, every build |
| Running on AEM | Local AEM SDK 2026.7 author with WKND content, 2026-11-17: `scripts/verify-aem-sdk.sh` passes; backfill of 220 assets in ~2 s; listener, delete, ontology extension, Oak index usage (query plan), per-user access filtering and semantic search (Ollama + Qdrant, 187 assets) checked by hand |
| Also on the local SDK | Vocabulary candidates, approve and merge; tag schemes and specific indexing; EMEA expansion search; CCVAR on a Sites page (render, editor, model JSON, binary pass-through, escaping); write-back with the narrow ACL; invalidation job targets the right page |
| Not run | Publish instance, dispatcher and CDN; a real external triplestore; AEM as a Cloud Service; load beyond a few hundred assets |

## Limitations

- Semantic search needs an embeddings endpoint and Qdrant; without them it is off.
- The SHACL validator implements a subset of SHACL Core, and inference is not OWL reasoning.
- Assets inside a moved folder are not re-indexed automatically; call the reindex endpoint.
- Changing the JCR layout of an existing graph needs a migration; none is provided. Reindexing
  rebuilds pipeline-owned statements but not reviewed AI facts.
- The `/bin/aemassets` endpoints are for author. Keep them blocked on the publish dispatcher.
- `aem.sdk.api` must not be newer than the target runtime; see
  [operations](docs/06-operations.md#build-target).

Details: [docs/11-context-engine.md](docs/11-context-engine.md#what-this-does-not-do).

## License

Apache License 2.0. See [LICENSE](LICENSE).
