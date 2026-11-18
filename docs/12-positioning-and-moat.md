# Positioning: an accelerator for AEM Assets

Written 2026-11-17 against what Adobe documents for AEM as a Cloud Service at that date. Recheck
section 2 before using this externally: Adobe ships monthly.

## The one-line position

**SemanticDAM is an accelerator that adds a governed knowledge layer to AEM Assets. It makes the
metadata that Adobe's AI Search, GenAI metadata, Content Discovery agent and AEM MCP Server work
on consistent, validated, explainable and connected.**

It does not replace any Adobe capability. It is installed into the customer's AEM as a Cloud
Service program as one bundle, uses AEM's own repository, tags, permissions and job queues, and
leaves Adobe's features as the primary experience for authors and agents.

## 1. Why customers need it alongside Adobe's AI

Adobe's AI features are only as good as the metadata and structure underneath them. In real DAMs:

- the same fact arrives from AEM authors, PIM and Workfront under different property names and
  with different values, and nobody can say which one is right;
- AI-generated metadata adds values, but nothing checks them against what valid metadata is for
  this customer;
- search finds similar assets, but a campaign manager needs to know *which* assets depict a product
  or belong to a campaign, and why;
- the customer's information model (brands, products, campaigns, taxonomy, rules) lives in
  spreadsheets and people's heads, not in the platform.

SemanticDAM fills that gap and hands the result back to AEM and to Adobe's agents.

## 2. How it fits with what Adobe ships

| Adobe capability (documented) | What SemanticDAM adds to it | Source |
|---|---|---|
| **AI Search** in AEM Assets: natural-language and semantic search (2026.01) | Clean, validated, de-duplicated metadata to search over; authoritative values when sources disagree; tag hierarchy made explicit | [2026.01.0 release overview](https://experienceleague.adobe.com/en/docs/events/aemcs-release-update-recordings/2026/2026-1-0) |
| **GenAI metadata** generation | Checks generated values against the customer's shapes; optional human review with an audit trail before a value becomes authoritative | [GenAI metadata](https://experienceleague.adobe.com/en/docs/experience-manager-learn/assets/metadata/genai-metadata) |
| **Content Discovery agent**: semantic matching plus metadata filters | Governed facts and entity relationships for the agent to filter and reason on ("assets depicting product X in campaign Y") | [Content Discovery agent](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/ai-in-aem/agents/discovery/overview) |
| **AEM MCP Server** (unified, 2026.7): asset search, metadata edit, publish, references | A companion read-only context tool set: facts with provenance, validity, related assets with reasons, relationship paths. Agents use Adobe's server to act and SemanticDAM's to understand | [2026.7.0 release notes](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/release-notes/release-notes/2026/2026-7-0), [Using MCP with AEMaaCS](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/ai-in-aem/mcp-support/using-mcp-with-aem-as-a-cloud-service) |
| **AEM tags and metadata schemas** | Tags become a taxonomy with inference (an asset tagged *Italy* is also *EMEA*); schema fields gain meaning, trust and validation | AEM platform |
| **Content Hub**, Smart Collections | Readiness rules ("ready for AJO") defined as shapes and raised as events; collections can be driven from governed facts | [Content Hub event](https://experienceleague.adobe.com/en/on-demand-events/intelligent-discovery-governed-distribution) |

Where Adobe already does the job, SemanticDAM defers to it:

- **Visual and natural-language search** is AEM AI Search. SemanticDAM's own `search.json` only
  embeds metadata text; it exists for environments without AI Search and for agent grounding, and
  should not be positioned as an alternative.
- **Acting on assets** (upload, move, publish, edit) is the AEM MCP Server. SemanticDAM's MCP tools
  are read-only by design.
- **Generating metadata** is GenAI metadata. SemanticDAM's proposal workflow is the review and
  governance step, and can sit behind any model, including Adobe's.

What I did not find in Adobe's documentation: a knowledge graph of asset entities, per-fact
provenance and trust across source systems, SHACL-style validation, or explanations of why two
assets are related. Those are the additions. ("Not found in a search", not proof of absence.)

## 3. What the accelerator delivers

| Value | How | Status |
|---|---|---|
| **One trusted value per fact** | Per-statement source, trust and validity; authoritative view with precedence rules | Working |
| **Validated metadata** | SHACL shapes from the customer's model; violations with readable messages; bundles as closed shapes | Working |
| **Connected assets** | Brands, products, campaigns, concepts and AEM tags as entities; related assets ranked by shared rare entities, with reasons; relationship paths | Working |
| **Customer-owned information model** | Mappings, shapes, taxonomy, trust and rules in Turtle under `/conf`; no rebuild to change | Working |
| **Governed AI** | Proposals checked against the model, human-approved, audited; model output cannot claim a trusted source | Working |
| **Context for agents and apps** | `context.json`, MCP tools, content variables, JSON-LD, all filtered by the caller's access | Working |
| **Readiness signals** | Shapes that raise events when an asset becomes ready for a channel | Working, no forwarder to Adobe I/O Events yet |

Time-to-value is the accelerator argument: a customer gets a governed metadata model, validation,
relationships and agent context in their existing AEM program by writing Turtle and configuring a
few OSGi settings, instead of a custom project.

## 4. What makes it hard to replace (the durable part)

The code is not the durable part. One bundle could be rebuilt by a strong AEM team in weeks, and
the standards it uses (Turtle, SHACL, SKOS, SPARQL) are deliberately open.

What accumulates and is costly to recreate:

1. **The customer's curated model**: mappings, shapes, trust levels, taxonomy and rules, tuned to
   their sources over time.
2. **Reviewed facts and their audit trail**: every human decision on AI proposals.
3. **Reconciled entities**: one brand, product or campaign across PIM, Workfront and AEM.

These belong to the customer and are portable, which is what makes an accelerator acceptable to
an enterprise. Their value grows the longer it runs.

## 5. Roadmap to deepen the fit with Adobe

Ordered by impact for the effort.

1. **Write governed facts back into AEM metadata fields** (authoritative value, validity flag) so
   that AEM AI Search, Smart Collections, the Content Discovery agent and the AEM MCP Server all
   benefit without knowing SemanticDAM exists. *Not implemented; the most important next step for
   an accelerator.*
2. **Use AEM metadata schemas as an ontology source**: generate mappings and shapes from the
   customer's existing schema forms instead of asking them to write Turtle.
3. **Forward READY events to Adobe I/O Events** so AJO and Workfront can react.
4. **Run the proposal review on GenAI metadata output** as well as on other providers.
5. **Vertical starter packs** (retail/CPG, financial services, media): ontology, mappings, shapes
   and readiness rules that work on day one.
6. **Evidence**: on a customer DAM, compare agent answers grounded on governed facts with answers
   from search alone (accuracy, provenance, wrong-asset rate).

## 6. Alignment with the Ontology Pipeline

Measured against Jessica Talisman's Ontology Pipeline (controlled vocabulary -> metadata standards
-> taxonomy -> thesaurus -> ontology -> knowledge graph), as implemented on 2026-11-17:

| Stage | Implementation |
|---|---|
| Controlled vocabulary | Every term has a `skos:definition` (test-enforced). Entity values resolve against governed entries; values from a non-authoritative source become candidates for a steward |
| Metadata standards | Dublin Core, schema.org, SKOS, SHACL, PROV-O; the application profile documents which property is used how |
| Taxonomy | SKOS concept schemes (one per AEM tag namespace), top concepts, `skos:broader`; assets indexed at the most specific concept |
| Thesaurus | Alternative labels (synonyms, tag translations) and hidden labels (variants, merged misspellings) drive entity resolution |
| Ontology | OWL classes and properties in the domain ontology; validation, mappings and rules kept apart in the application profile |
| Knowledge graph | Assets, entities and schemes as nodes; provenance with PROV-O; exportable as Turtle or stored in a SPARQL triplestore |

Not yet: language-tagged labels, `skos:related`, and homonym disambiguation within one class.

## 7. Overlap to watch

| If Adobe adds | Then |
|---|---|
| Per-fact provenance or validation in AEM Assets | Map SemanticDAM's model onto it and keep the customer-owned model and governance workflow on top |
| A native asset knowledge graph | Export to it (Turtle / SPARQL); SemanticDAM becomes the model and rules layer that feeds it |
| Governed review for GenAI metadata | Retire the proposal workflow; keep validation and relationships |

Because everything is in open standards, each of these is a migration path, not a dead end.

## 8. Claims you can and cannot make

Can, with evidence in this repo:

- adds per-fact provenance, trust and validation to AEM Assets metadata from AEM, PIM, Workfront
  and AEM tags;
- explains how assets are related;
- governs brands, products, campaigns and categories as controlled vocabularies with steward review;
- pages can show governed facts with full-page caching kept (targeted dispatcher invalidation);
- AI suggestions do not become authoritative without a recorded human approval;
- agents and apps only see what the calling user may see;
- runs inside AEM as a Cloud Service as one bundle; verified on the AEM SDK.

Cannot, yet:

- that it improves AEM AI Search results (needs write-back, item 1 of the roadmap, and a measurement);
- production scale beyond a few hundred assets;
- deployment on an AEM as a Cloud Service environment (only the SDK so far);
- measured business impact.
