# Decisions

## D01 - Own Turtle parser, Apache Jena as the test oracle (supersedes "minimal ontology parsing")

The bundle parses Turtle and validates SHACL itself (`rdf.TurtleParser`, `ontology.ShaclValidator`)
and has no RDF runtime dependency. Embedding Jena or RDF4J in an AEM as a Cloud Service bundle
means carrying their dependency trees and `ServiceLoader` initialisation inside OSGi, which is a
bundle-resolution risk on every SDK update. Instead Jena is a test-scope dependency: the build
fails if our parser reads the bundled ontology differently from Jena, or if our validator reports
different violations than Jena SHACL for the same data.

Consequences: the validator covers a stated subset of SHACL Core (see `ShaclValidator`). A
constraint outside that subset is ignored, so add it to the validator and to the Jena comparison
test before using it in a shape.

## D02 - Ontology as code, extensions as content

The core ontology lives in `core/src/main/resources/ontology` and is versioned with the code.
Project-specific classes, mappings, shapes, taxonomy and rules are added as Turtle files in the
repository and listed in the `SemanticDAM - Ontology` OSGi configuration, so a customer can extend
the model without forking the bundle.

## D03 - The ontology is the single source of truth

Java never hardcodes a mapping, validation pattern, bundle definition or inference rule. The few
IRIs code needs by name are constants in `ontology.Vocabulary`, and a test fails if one of them is
not declared in the ontology.

## D04 - Entities are nodes

Brand, product, campaign and category are entity nodes with their own IRI, type and label. Assets
link to them. Two assets are related when they share an entity, never because they share a literal
such as a mime type.

## D05 - Statement ownership by source

The ingest pipeline rebuilds only the statements whose source is an ontology source system or the
inference engine. Statements from other sources (reviewed AI facts, `AI:<model>`) are preserved
across re-ingest.

## D06 - Access control at the API, not in the store

The graph is stored under `/var/semanticdam` and read with a service user. Every endpoint, including
the MCP server, filters results by the caller's own read access to the DAM asset.

## D07 - Domain ontology separate from the application profile

`asset-ontology.ttl` says what things are and what words mean; `application-profile.ttl` says how
this deployment maps, validates, trusts and infers. Projects change the profile, rarely the
ontology, and the ontology can be reviewed on its own.

## D08 - Controlled vocabularies with candidate entries

A value from a source that is not the authority for a vocabulary does not silently create an
entry. It becomes a CANDIDATE, links to it are invalid, and a steward approves, rejects or merges
it. Authorities (PIM for products, Workfront for campaigns, AEM tags) create approved entries.
Entries stored before statuses existed are read as APPROVED.

## D09 - Index at the most specific concept

`dcterms:subject` holds only the concepts asserted for the asset. Ancestors are derived into
`sdam:broaderSubject`, which search treats as an expansion of `dcterms:subject` and relatedness
ignores. This keeps indexing specific while still finding Italy assets under EMEA.

## D10 - Provenance with PROV-O

Statement sources are PROV agents with IRIs (`agent:PIM`, `agent:AI%3A<model>`). Exports and the
SPARQL store use `prov:wasAttributedTo`; the internal label is kept for readability.

## D11 - Targeted dispatcher invalidation over SDI for governed facts

Facts on publish change only when an asset is republished, so invalidating exactly the pages that
reference that asset keeps full-page caching. SDI stays an option for components that must be
fresh per request; it does not help the CDN layer, which caches the assembled page.

## D12 - Compile against the oldest supported runtime

`aem.sdk.api` stays at or below the runtime deployed to, because bnd derives import ranges from it.
