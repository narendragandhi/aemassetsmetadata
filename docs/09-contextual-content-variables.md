# Contextual Content Variables (native)

SemanticDAM includes an optional native implementation of the ACS AEM Commons
Contextual Content Variables content contract. It does not depend on ACS AEM
Commons and does not require that bundle to be installed.

The compatibility target is the server-side token form documented by ACS:

```text
((prefix.propertyName))
((prefix.propertyName!action))
```

The built-in prefixes are:

- `page_properties` — reads the current page’s `jcr:content` properties;
- `inherited_page_properties` — walks the current page and ancestors, keeping
  the nearest value for each property.
- `semantic_properties` — reads governed SemanticDAM facts from the knowledge
  graph for the current asset, exposing predicate local names such as `sku`,
  `brand`, and `belongsToCampaign`.

The built-in action is `url`, for example:

```text
Search: ((page_properties.searchTerm!url))
Language: ((inherited_page_properties.jcr:language))
SKU: ((semantic_properties.sku))
```

## Governed AI workflow

`semantic_properties` reads only facts already projected into the governed
graph. The complete loop is: POST to propose, inspect persisted `PROPOSED`
records, authenticate a steward review, and project only approved facts.
Rejection persists the decision but does not change graph metadata.

```bash
ASSET=/content/dam/semanticdam/asset.jpg
curl -u admin:admin -X POST -d "path=$ASSET" http://localhost:4502/bin/aemassets/ai/propose
curl -u admin:admin "http://localhost:4502/bin/aemassets/ai/proposals.json?path=$ASSET"
curl -u admin:admin -X POST \
  --data-urlencode "path=$ASSET" \
  --data-urlencode "predicate=https://example.com/aem-assets-ontology#sku" \
  --data-urlencode "object=SUM-2026-X1" --data-urlencode "approve=true" \
  --data-urlencode "reason=verified by DAM steward" \
  http://localhost:4502/bin/aemassets/ai/review
```

Configure an `AiEnrichmentProvider` before invoking `propose`; the workflow
remains inactive when no provider is configured.

## Activation

The response filter is opt-in and uses the OSGi configuration PID generated for
`com.semanticdam.core.core.contextvars.ContextualContentVariableConfig`.
Enable HTML and/or model JSON rewriting only after validating response size,
dispatcher behavior, cache variation, and publish performance.

With no configuration, no response rewriting occurs. Existing AEM rendering is
unchanged.

## Extension points

Register an OSGi service implementing `ContextualContentVariableProvider` to
add a prefix and property map. Register an OSGi service implementing
`ContextualContentVariableAction` to add a transform action. Providers should
be tenant/path-scoped and must not expose secrets or unrestricted repository
data.

## Why this complements SemanticDAM

`semantic_properties` uses the same `KnowledgeGraphService` as the metadata
APIs and relationship queries. Authored text can therefore consume a
canonical, source-aware fact instead of duplicating a raw AEM property mapping.
The provider returns the first governed statement for a local predicate name;
use the graph/API response when callers need all assertions or provenance.

## Implementation boundary

The native implementation uses a bounded response buffer for HTML and model
JSON. It escapes replacements for JSON and leaves unresolved tokens unchanged.
It is a compatibility implementation, not a claim of byte-for-byte parity with
ACS internals. For high-volume publish traffic, prefer a Sling Rewriter
pipeline or an equivalent streaming implementation after performance testing.

The source implementation is under:

```text
core/src/main/java/com/semanticdam/core/core/contextvars
```

The resolver tests are runnable with:

```bash
mvn -B -pl core -Dtest=ContextualContentVariableServiceTest test
```
