# The context engine

SemanticDAM answers one question for people, applications and AI agents: **what is known about
this asset, how sure are we, and what is it connected to?** This page describes the model behind
the answer and the three ways to ask.

## The model

| Layer | What it is | Where it lives |
|---|---|---|
| Ontology | Classes, properties, SHACL shapes, DAM property mappings, source trust, SKOS taxonomy, inference rules | `asset-ontology.ttl` plus optional extension files |
| Graph | Asset nodes with statements; entity nodes for brands, products, campaigns, concepts and AEM tags | `/var/semanticdam/graph`, `/var/semanticdam/entities` (or a SPARQL triplestore) |
| Vectors (optional) | One embedding per asset, built from its governed facts | Qdrant, filled by an embedding job after each ingest |
| Statement | predicate, object (literal or entity IRI), source, confidence, validity | one JCR node each |
| Proposals | AI-suggested statements and the audit trail of their review | `/var/semanticdam/proposals` |

How a DAM property becomes context:

1. A change to `<asset>/jcr:content/metadata` queues a Sling job.
2. The mapper applies the ontology's `sdam:PropertyMapping`s. A mapping with an `sdam:entityClass`
   links the asset to an entity node; the label is reconciled against the taxonomy first
   (`"Organic"` resolves to the declared concept *Organic Cotton*), otherwise an IRI is minted.
3. Statements the pipeline does not own (reviewed AI facts) are carried over.
   AEM tags (`cq:tags`) become concepts: the tag title is the label and the parent tag is
   `skos:broader`.
4. Inference adds `skos:broader` ancestors (ontology taxonomy and tag parents) and the consequents
   of matching `sdam:InferenceRule`s.
5. Every statement is validated against the SHACL shapes for the asset's classes. Invalid
   statements are kept and flagged, not dropped.
6. The asset and its entities are written. If the asset now conforms to a readiness shape it did
   not conform to before, `com/semanticdam/asset/READY` is posted.
7. If semantic search is configured, an embed job turns the asset's valid facts into text
   ("Brand: Summit. Subject: Cycling, Italy, EMEA.") and stores its embedding.

## Asking for context

### HTTP

| Endpoint | Returns |
|---|---|
| `GET /bin/aemassets/context.json?path=&related=10` | The full context document (below) |
| `GET /bin/aemassets/related.json?predicate=&value=` (or `sku=`, `campaign=`) | Assets with a fact. `predicate` is a property IRI or label; `value` may be an entity label |
| `GET /bin/aemassets/suggestions.json?path=&limit=` | Assets sharing an entity with the asset, which entities, and a relatedness score |
| `GET /bin/aemassets/search.json?q=&limit=&predicate=&value=` | Assets closest in meaning to a query; optional fact boost. 409 when not configured |
| `GET /bin/aemassets/metadata.json?path=&resolved=true` | Raw or authoritative statements |
| `GET /bin/aemassets/metadata.ttl?path=` | The asset and its entities as Turtle / N-Triples |
| `POST /bin/aemassets/reindex?path=&limit=` | Queues ingest for every asset below a path (administrators) |
| `GET /bin/aemassets/vocabulary/candidates.json?class=Brand` | Candidate vocabulary entries and the assets using them (stewards) |
| `POST /bin/aemassets/vocabulary/review` | `entity`, `decision`=approve/reject/merge, `target`, `reason` (stewards) |
| `POST /bin/aemassets/invalidate?asset=` | Queues dispatcher invalidation of the pages showing an asset's facts (author) |

### MCP (for agents)

`POST /bin/aemassets/mcp` is a Model Context Protocol server with read-only tools:

| Tool | Purpose |
|---|---|
| `describe_ontology` | The vocabulary: classes, properties (literal or entity valued), concepts, mappings |
| `get_asset_context` | The context document for one asset |
| `find_assets` | Assets by predicate and value, e.g. `{"predicate":"Brand","value":"Summit"}` |
| `related_assets` | Assets that share an entity with an asset |
| `explain_relationship` | The shortest chain of shared entities between an asset and another asset or entity |
| `semantic_search` | Assets closest in meaning to a natural-language query (listed only when configured) |

Related assets are ranked by the shared entities, each weighted `1 / ln(1 + n)` where n is the
number of assets linked to that entity, and halved when the link was only inferred. A shared
product or specific tag outweighs a shared top-level tag that every asset inherits.

```bash
curl -u admin:admin -H 'Content-Type: application/json' http://localhost:4502/bin/aemassets/mcp \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_asset_context","arguments":{"path":"/content/dam/semanticdam/asset.jpg"}}}'
```

Scope of the implementation: one JSON-RPC message per POST answered with one JSON response
(the stateless part of the Streamable HTTP transport). No sessions, no SSE stream (GET returns
405), no resources or prompts. Protocol revisions `2025-06-18`, `2025-03-26` and `2024-11-05` are
accepted. A request carrying an `Origin` header from another host is rejected.

### In authored content

`((semantic_properties.brand))` in page text resolves to the asset's brand label; see
[09-contextual-content-variables.md](09-contextual-content-variables.md). `SemanticSeoService`
renders the authoritative schema.org facts as a JSON-LD `ImageObject`.

## Access control

The graph is read with a service user, so the endpoints enforce access themselves: an asset the
caller cannot read through their own session is reported as not found, and is left out of related
assets, search results and relationship paths. Proposing and reviewing AI facts requires
permission to edit the asset's metadata. Reindexing requires write access to
`/var/semanticdam/graph`.

## What this does not do

- **Semantic search needs two external services:** an OpenAI-compatible embeddings endpoint and
  Qdrant. Without them `search.json` answers 409 and the MCP tool is not listed.
- **No OWL reasoning.** Inference is `skos:broader` closure plus declarative if/then rules.
- **A subset of SHACL Core.** See `ShaclValidator` for the supported constraints.
- **Entities carry a type, a label and broader concepts only.**
- **Labels carry no language tag.** Tag translations become alternative labels without their locale.
- **Homonyms within one class** (two different brands both called "Apex") are not distinguished;
  classes keep them apart across vocabularies, and a steward merge or rejection handles the rest.

## Sample output

Captured from a local AEM SDK (2026.7) author with the WKND sample content on 2026-11-17, ontology
3.0.0 with profile-1. Long arrays are shortened, marked `"..."`.

`GET /bin/aemassets/context.json?path=/content/dam/wknd-shared/en/adventures/cycling-tuscany/cycling-tuscany&related=2`

```json
{
  "path": "/content/dam/wknd-shared/en/adventures/cycling-tuscany/cycling-tuscany",
  "ontologyVersion": "3.0.0+profile-1",
  "types": [
    "https://semanticdam.com/ontology/core#Asset"
  ],
  "facts": [
    {
      "predicate": "http://purl.org/dc/terms/subject",
      "label": "Subject",
      "value": "https://semanticdam.com/id/tag/wknd-shared/activity/cycling",
      "valueIsEntity": true,
      "valueLabel": "Cycling",
      "source": "AEM",
      "sourceAgent": "https://semanticdam.com/id/agent/AEM",
      "confidence": 0.75,
      "valid": true,
      "authoritative": true
    },
    {
      "predicate": "http://purl.org/dc/terms/subject",
      "label": "Subject",
      "value": "https://semanticdam.com/id/tag/wknd-shared/season/summer",
      "valueIsEntity": true,
      "valueLabel": "Summer",
      "source": "AEM",
      "sourceAgent": "https://semanticdam.com/id/agent/AEM",
      "confidence": 0.75,
      "valid": true,
      "authoritative": true
    },
    {
      "predicate": "http://purl.org/dc/terms/subject",
      "label": "Subject",
      "value": "https://semanticdam.com/id/tag/wknd-shared/season/spring",
      "valueIsEntity": true,
      "valueLabel": "Spring",
      "source": "AEM",
      "sourceAgent": "https://semanticdam.com/id/agent/AEM",
      "confidence": 0.75,
      "valid": true,
      "authoritative": true
    },
    {
      "predicate": "http://purl.org/dc/terms/subject",
      "label": "Subject",
      "value": "https://semanticdam.com/id/tag/wknd-shared/region/emea/italy",
      "valueIsEntity": true,
      "valueLabel": "Italy",
      "source": "AEM",
      "sourceAgent": "https://semanticdam.com/id/agent/AEM",
      "confidence": 0.75,
      "valid": true,
      "authoritative": true
    },
    {
      "predicate": "https://semanticdam.com/ontology/core#broaderSubject",
      "label": "Broader Subject",
      "value": "https://semanticdam.com/id/tag/wknd-shared/activity",
      "valueIsEntity": true,
      "valueLabel": "Activity",
      "source": "Inference-Engine",
      "sourceAgent": "https://semanticdam.com/id/agent/Inference-Engine",
      "confidence": 0.75,
      "valid": true,
      "authoritative": true
    },
    {
      "...": "more facts"
    }
  ],
  "entities": [
    {
      "uri": "https://semanticdam.com/id/tag/wknd-shared/activity/cycling",
      "type": "http://www.w3.org/2004/02/skos/core#Concept",
      "label": "Cycling",
      "status": "APPROVED",
      "scheme": "https://semanticdam.com/id/tag/wknd-shared",
      "broader": [
        "https://semanticdam.com/id/tag/wknd-shared/activity"
      ]
    },
    {
      "uri": "https://semanticdam.com/id/tag/wknd-shared/season/summer",
      "type": "http://www.w3.org/2004/02/skos/core#Concept",
      "label": "Summer",
      "status": "APPROVED",
      "scheme": "https://semanticdam.com/id/tag/wknd-shared",
      "broader": [
        "https://semanticdam.com/id/tag/wknd-shared/season"
      ]
    },
    {
      "...": "more entities"
    }
  ],
  "violations": [],
  "readyFor": [],
  "related": [
    {
      "path": "/content/dam/wknd-shared/en/adventures/cycling-southern-utah/cycling-southern-utah",
      "facts": [
        {
          "predicate": "http://purl.org/dc/terms/subject",
          "label": "Subject",
          "value": "https://semanticdam.com/id/tag/wknd-shared/activity/cycling",
          "valueIsEntity": true,
          "valueLabel": "Cycling",
          "source": "AEM",
          "sourceAgent": "https://semanticdam.com/id/agent/AEM",
          "confidence": 0.75,
          "valid": true
        },
        {
          "...": "more facts"
        }
      ],
      "score": 1.57,
      "via": [
        {
          "predicate": "http://purl.org/dc/terms/subject",
          "label": "Subject",
          "entity": "https://semanticdam.com/id/tag/wknd-shared/activity/cycling",
          "entityLabel": "Cycling"
        },
        {
          "predicate": "http://purl.org/dc/terms/subject",
          "label": "Subject",
          "entity": "https://semanticdam.com/id/tag/wknd-shared/season/summer",
          "entityLabel": "Summer"
        },
        {
          "predicate": "http://purl.org/dc/terms/subject",
          "label": "Subject",
          "entity": "https://semanticdam.com/id/tag/wknd-shared/season/spring",
          "entityLabel": "Spring"
        }
      ]
    },
    {
      "path": "/content/dam/wknd-shared/en/adventures/whistler-mountain-biking/whistler-mountain-biking",
      "facts": [
        {
          "predicate": "http://purl.org/dc/terms/subject",
          "label": "Subject",
          "value": "https://semanticdam.com/id/tag/wknd-shared/activity/cycling",
          "valueIsEntity": true,
          "valueLabel": "Cycling",
          "source": "AEM",
          "sourceAgent": "https://semanticdam.com/id/agent/AEM",
          "confidence": 0.75,
          "valid": true
        },
        {
          "...": "more facts"
        }
      ],
      "score": 1.135,
      "via": [
        {
          "predicate": "http://purl.org/dc/terms/subject",
          "label": "Subject",
          "entity": "https://semanticdam.com/id/tag/wknd-shared/activity/cycling",
          "entityLabel": "Cycling"
        },
        {
          "predicate": "http://purl.org/dc/terms/subject",
          "label": "Subject",
          "entity": "https://semanticdam.com/id/tag/wknd-shared/season/spring",
          "entityLabel": "Spring"
        }
      ]
    }
  ]
}
```

Excerpt of `GET /bin/aemassets/metadata.ttl` for the same asset (SKOS and PROV):

```turtle
<https://semanticdam.com/id/asset/content/dam/wknd-shared/en/adventures/cycling-tuscany/cycling-tuscany> <http://purl.org/dc/terms/subject> <https://semanticdam.com/id/tag/wknd-shared/region/emea/italy> .
<https://semanticdam.com/id/tag/wknd-shared/region/emea/italy> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2004/02/skos/core#Concept> .
<https://semanticdam.com/id/tag/wknd-shared/region/emea/italy> <http://www.w3.org/2004/02/skos/core#prefLabel> "Italy" .
<https://semanticdam.com/id/tag/wknd-shared/region/emea/italy> <http://www.w3.org/2004/02/skos/core#hiddenLabel> "wknd-shared:region/emea/italy" .
<https://semanticdam.com/id/tag/wknd-shared/region/emea/italy> <http://www.w3.org/2004/02/skos/core#broader> <https://semanticdam.com/id/tag/wknd-shared/region/emea> .
<https://semanticdam.com/id/tag/wknd-shared/region/emea/italy> <http://www.w3.org/2004/02/skos/core#inScheme> <https://semanticdam.com/id/tag/wknd-shared> .
```

Steward queue right after the vocabulary model was introduced (`candidates.json`):

```json
{
  "count": 0,
  "candidates": []
}
```
