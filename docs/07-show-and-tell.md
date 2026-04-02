# Show And Tell

This guide demonstrates how the repo behaves end-to-end, with both AEM-authored and externally-ingested metadata.

## What You Can Demonstrate Now

1. Extract metadata from a DAM asset and map it to ontology predicates.
1. Index metadata into an in-memory knowledge graph.
1. Export triples as Turtle.
1. Query the graph and return normalized JSON.

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
