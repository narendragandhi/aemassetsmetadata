# Show and tell: the demo and its story

A 15-20 minute live demo for a mixed audience (DAM leads, information architects, marketers, AEM
architects). The script `demo/run-demo.sh` performs every step against a local AEM author and
prints what each act proves; this page is the talk track around it.

```bash
mvn clean install -PautoInstallSinglePackage   # once
bash demo/run-demo.sh                           # pauses after each act; --auto runs straight through
```

Needs the project deployed. Optional: WKND shared content (act 4) and semantic search configured
(act 7; see [operations](06-operations.md#semantic-search)). The script resets its own data on every
run and touches nothing else.

## The storyline

**Adobe's AI finds and acts on assets. SemanticDAM makes the facts underneath them trustworthy:
consistent, validated, explainable, connected, and owned by the customer.** Every act shows one
piece of that sentence. Never position it against Adobe's features; it feeds them.

| Act | Show | Say | Value | Architecture element |
|---|---|---|---|---|
| 0 The picture | The architecture diagram | "One bundle inside AEM. Sources write metadata as today; SemanticDAM governs it and hands it back to pages, search and agents." | Fits the existing AEM program; nothing to replatform | Whole system ([architecture](02-architecture.md#1-system-context)) |
| 1 The problem | Raw metadata of a new asset from three systems | "This is what AI Search and agents work on today. Which brand is official? Is that a valid Workfront ID?" | Names the pain every DAM team knows | DAM metadata as the source |
| 2 Governed facts | The same asset as governed facts | "Every fact now says where it came from, how much we trust that source, and whether it is valid. 'Organic' became our governed concept. The bad ID is flagged with the rule's own message." | Trust and data quality without blocking authors | Mapping, controlled vocabulary, SHACL validation, inference, PROV |
| 3 Controlled vocabulary | Candidate brand, typo on a second asset, approve + merge | "Authors aren't the authority for brands, so a new brand waits for a steward. A typo is merged once, and from then on resolves by itself." | Governance a DAM lead can run; fewer duplicates | Vocabulary policy in the profile, steward workflow, hidden labels |
| 4 Taxonomy from AEM tags | Tag concepts, broader subjects, EMEA search | "We reuse the tags you already have as a real taxonomy. Assets stay indexed at 'Italy', yet a search for EMEA finds them." | Better findability with no re-tagging | SKOS schemes, specific indexing, `sdam:broaderSubject` expansion |
| 5 Connected assets | Related assets with reasons | "Related doesn't mean 'looks similar'. It means 'shares this product or campaign', and we can say which." | Reuse and campaign consistency | Entity graph, rarity-weighted ranking |
| 6 Facts on a page | Visitor vs author render of a text component | "Marketers write `((semantic_properties.brand))` once; the page always shows the governed value. Pages stay fully cached; only pages using this asset are refreshed when it changes." | No duplicate authoring; performance kept | Write-back to asset metadata, CCVAR filter, targeted invalidation |
| 7 Agents | MCP tools, `explain_relationship`, `semantic_search` | "Adobe's AEM MCP Server lets an agent act on assets; these tools let it understand them, with provenance, and only what the user may see." | Grounded, permission-aware AI | MCP endpoint, context service, access filtering |
| Value by audience | The persona table | Pick the two rows that match the room | Each stakeholder hears their outcome | - |

## Talking points for questions

- **"Doesn't Adobe already do this?"** Adobe does search, generation and agent actions. What we
  could not find documented is per-fact provenance across sources, validation against a customer
  model, or explained relationships. Those are what this adds, and they make Adobe's features work
  on better data. See [positioning](12-positioning-and-moat.md).
- **"Who maintains the model?"** An information architect, in Turtle under `/conf`, no release
  needed; stewards handle the day-to-day through the review endpoints.
- **"What about caching on publish?"** Facts travel with the asset; pages stay fully cached and
  only pages referencing a republished asset are invalidated.
- **"Lock-in?"** Everything is open standards (SKOS, SHACL, PROV-O, SPARQL) and exportable as Turtle.

## Be honest in the room

- Verified on a local AEM SDK author. Publish, dispatcher and CDN behaviour have not been tested
  on a Cloud Service environment yet; in the demo the invalidation request has no publish agent to
  go to.
- There is no steward user interface yet; act 3 uses HTTP endpoints.
- Demo data is WKND plus generated assets, not a customer DAM; no business impact is measured yet.

## Recovering during a live demo

- An act prints "timed out waiting": jobs are slower than usual; press Enter and continue, or rerun
  the script (it resets).
- Endpoints answer 404 with "No resource found": the bundle is not active. Check
  `/system/console/bundles` for `semanticdam.core`. Once, after a quick redeploy, it stayed in
  "Installed" and had to be started from the console.
- Act 7 shows no `semantic_search`: semantic search is not configured; the other tools still work.

## Older material

The earlier flows (AI proposal review, CCVAR basics, external ingestion) are still valid features;
see [AI readiness](08-ai-readiness.md), [CCVAR](09-contextual-content-variables.md) and
[operations](06-operations.md).
