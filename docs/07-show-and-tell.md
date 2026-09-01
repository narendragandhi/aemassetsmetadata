# Show And Tell

This guide demonstrates how the repo behaves end-to-end, with both AEM-authored and externally-ingested metadata.

## What You Can Demonstrate Now

1. Extract metadata from a DAM asset and map it to ontology predicates.
1. Index metadata into an in-memory knowledge graph.
1. Export triples as Turtle.
1. Query the graph and return normalized JSON.
1. Run a live vector search against self-hosted Qdrant.
1. Demonstrate AI proposal governance without making a model call.
1. Resolve ACS-compatible contextual content variables without ACS AEM Commons.
1. Run the complete governed AI loop: propose, inspect, approve/reject, and
   project only approved facts into the graph.

## Demo Flow (AEM Authoring)

1. Create or upload an asset under `/content/dam`.
1. Set asset metadata on `/content/dam/<asset>/jcr:content/metadata`.
1. Ensure the metadata contains `dc:title`, `dc:description`, `dc:format`, and `aem:brand`.
1. Call the JSON endpoint: `GET /bin/aemassets/metadata.json?path=/content/dam/<asset>`
1. Call the Turtle endpoint: `GET /bin/aemassets/metadata.ttl?path=/content/dam/<asset>`

## Demo Flow (With Included Content Package)

1. Deploy the project with `mvn clean install -PautoInstallSinglePackage`.
1. Use the demo asset at `/content/dam/semanticdam/asset.jpg`.
1. Run the demo script: `bash demo/curl-demo.sh`.

## Demo Flow (AI and Vector Readiness)

This is the smallest honest demo of the AI architecture. It proves the
provider boundary, proposal policy/review behavior, and vector backend without
requiring paid credentials:

```bash
# From the repository root, with Colima/Docker and Qdrant running:
bash scripts/verify-qdrant.sh

# Provider parsing, policy, approval, persistence, resilience, and registry:
mvn -B -pl core -Dtest='*Ai*Test,*OpenAiCompatible*Test,*Resilient*Test' test
```

For the real gateway, provide the protected OSGi configuration documented in
`docs/06-operations.md`, then use an approved test asset and run the AEM SDK
verifier. The provider proposes facts; the review step remains explicit. This
demo does not claim autonomous publishing.

## Demo Flow (Contextual Content Variables)

Run the focused resolver test:

```bash
mvn -B -pl core -Dtest=ContextualContentVariableServiceTest test
```

Then configure the native CCVAR PID and author text such as
`Welcome ((page_properties.title))` or
`((inherited_page_properties.jcr:language))`. The filter is disabled until
explicitly enabled, and should be performance-tested before publish rollout.

## Demo Flow (Governed AI Review)

This is an explicit, auditable workflow—not autonomous publishing. It requires
an enabled provider and an authenticated AEM user:

```bash
ASSET=/content/dam/semanticdam/asset.jpg
curl -u admin:admin -X POST -d "path=$ASSET" http://localhost:4502/bin/aemassets/ai/propose
curl -u admin:admin "http://localhost:4502/bin/aemassets/ai/proposals.json?path=$ASSET"
curl -u admin:admin -X POST \
  --data-urlencode "path=$ASSET" \
  --data-urlencode "predicate=https://example.com/aem-assets-ontology#sku" \
  --data-urlencode "object=SUM-2026-X1" --data-urlencode "approve=true" \
  --data-urlencode "reason=verified by steward" \
  http://localhost:4502/bin/aemassets/ai/review
```

`propose` stores `PROPOSED` records under `/var/semanticdam/proposals` and
does not write the graph. Only approval projects the selected fact.

For a quick author demo, add the **AI Metadata Review** component to any page,
select a DAM asset in its dialog, and open the page while logged into Author.
The component is delivered in `ui.apps`; it is not enabled on Publish pages.

## Demo Flow (External Metadata Ingestion)

1. External system writes fields to `/content/dam/<asset>/jcr:content/metadata`.
1. The `AssetMetadataResourceChangeListener` detects changes and triggers indexing.
1. The mapper normalizes fields into ontology-backed predicates.
1. Use the same endpoints to show normalized output.

## Expected JSON Shape

The JSON output is a normalized list of predicate/object statements:

```json
{
  "assetId": "/content/dam/semanticdam/asset.jpg",
  "statements": [
    {
      "predicate": "http://purl.org/dc/terms/title",
      "object": "Demo Asset 01",
      "objectIsUri": false,
      "source": "AEM"
    },
    {
      "predicate": "http://purl.org/dc/terms/description",
      "object": "Show-and-tell asset for knowledge graph metadata",
      "objectIsUri": false,
      "source": "AEM"
    },
    {
      "predicate": "https://example.com/aem-assets-ontology#sku",
      "object": "SUM-2026-X1",
      "objectIsUri": false,
      "source": "PIM"
    },
    {
      "predicate": "https://example.com/aem-assets-ontology#belongsToCampaign",
      "object": "Global Summit 2026",
      "objectIsUri": false,
      "source": "Workfront"
    }
  ]
}
```

## Expected Turtle Shape

```turtle
<https://example.com/aem-assets/asset/content/dam/semanticdam/asset.jpg> <http://purl.org/dc/terms/title> "Demo Asset 01" .
<https://example.com/aem-assets/asset/content/dam/semanticdam/asset.jpg> <http://purl.org/dc/terms/description> "Show-and-tell asset for knowledge graph metadata" .
<https://example.com/aem-assets/asset/content/dam/semanticdam/asset.jpg> <http://purl.org/dc/terms/format> "image/jpeg" .
<https://example.com/aem-assets/asset/content/dam/semanticdam/asset.jpg> <https://example.com/aem-assets-ontology#brand> "Summit" .
```

## Mapping Rules

Mapping rules live in `core/src/main/java/com.semanticdam.core/core/application/AemAssetMetadataMapper.java`. If your external system uses different fields, add them there and map to ontology predicates.

## Storage Notes

The default adapter is in-memory. For production, switch to `sparql` and implement a real `SparqlResultParser` for query responses.
