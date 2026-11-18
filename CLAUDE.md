# SemanticDAM (aemassetsmetadata)

Context engine for AEM Assets: ontology-driven knowledge graph over DAM metadata, served over
HTTP and MCP. Read `docs/11-context-engine.md` and `docs/02-architecture.md` first.

## Commands

- Core tests: `mvn -B -pl core test`
- Full build (packages, FileVault validation, AEM analyser):
  `CYPRESS_INSTALL_BINARY=0 MAVEN_OPTS="-Xmx4g" mvn clean install`
- Deploy to a running local author: `mvn clean install -PautoInstallSinglePackage`,
  then `./scripts/verify-aem-sdk.sh`

## Rules for changes

- `asset-ontology.ttl` (domain: classes, properties, taxonomy; every term needs a
  `skos:definition`) and `application-profile.ttl` (mappings, trust, shapes, rules, vocabulary
  entries) are the single source of truth; keep the split. Do not hardcode a mapping, pattern, bundle or inference rule in Java. IRIs that code needs
  by name go in `ontology.Vocabulary`; a test fails if they are not declared in the ontology.
- No RDF library at runtime. Apache Jena is test scope only and is the oracle for
  `TurtleParserTest` and `ShaclValidatorTest`. A new SHACL constraint must be added to
  `ShaclValidator` and to the Jena comparison test together.
- The graph is read with a service user. Every new endpoint or MCP tool must filter by the
  caller's access with `ApiSupport.readAccess(request)`.
- Store adapters throw `GraphStoreException` on failure. Never return an empty result for an error:
  the pipeline would treat it as "no data" and erase facts.
- Nothing may write AI output to the graph except `GovernedAiEnrichmentWorkflow.review`.
- Never auto-approve vocabulary entries from a non-authoritative source; use `sdam:entityPolicy`.
- Index assets at the most specific concept; derived ancestors go to `sdam:broaderSubject`.
- Only the invalidation service user may replicate; the main service user may only write
  `semanticdam:*` metadata properties.
- Service logins use the sub-service `JcrKnowledgeGraphStore.SERVICE_USER` (`semanticdam`).
- OSGi configuration files are named after the component's `configurationPid`. Check the generated
  `core/target/classes/OSGI-INF/*.xml` when adding one.
- To change the Oak index, add `semanticdam.statement-custom-2`; do not edit `-custom-1`.

## Known gaps

- Verified on a local AEM SDK 2026.7 author (2026-11-17), not on AEM as a Cloud Service.
- The SPARQL store is tested against Jena's in-memory engine only, not a running triplestore.
- `aem.sdk.api` must stay at or below the oldest runtime you deploy to (local SDK is 2026.7).
- Publish, dispatcher and CDN behaviour (write-back replication, page invalidation) not yet tested.
