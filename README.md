# SemanticDAM — AEM Asset Metadata Governance Starter

An AEM reference implementation for normalizing DAM metadata into a small governed vocabulary, preserving provenance, and querying relationships between assets. It is a starter kit and architecture reference, not a turnkey enterprise graph platform.

## What is implemented

* A metadata pipeline from DAM properties to canonical ontology predicates.
* Provenance, validity, and confidence on each metadata statement.
* In-memory and JCR graph storage adapters.
* Relationship and metadata endpoints for local demonstrations.
* A starter Turtle ontology and conflict-resolution service.

The SPARQL adapter can emit updates and build read queries, but result parsing and remote relationship queries are experimental. AI vision, graph visualization, and external-system ingestion are extension points, not completed product features.

## 🚀 Quick Start

1. **Build the project:**
   ```bash
   mvn clean install -DskipTests
   ```

2. **Deploy to a local AEM SDK:**
   ```bash
   mvn clean install -PautoInstallSinglePackage
   ```

3. **Try the metadata API:**
   Access the semantic suggestion API for any asset:
   ```bash
   curl -u admin:admin "http://localhost:4502/bin/aemassets/suggestions.json?path=/content/dam/semanticdam/asset.jpg"
   ```

## Architecture

The system uses a **Decoupled Metadata Pipeline**:
1. **Source:** reads metadata from an AEM DAM resource.
2. **Mapping and validation:** maps source properties to canonical predicates and validates them against the ontology.
3. **Pipeline:** applies conflict rules and indexes an `AssetMetadata` record.
4. **Storage:** uses the configured graph adapter (`in-memory` or `jcr`).
5. **Consumption:** exposes normalized metadata and relationship queries for AEM consumers.

## 📂 Project Structure

*   `core`: The Java "Brain" (Services, Domain Models, Graph Stores).
*   `ui.apps`: AEM Components (including the **Semantic Intelligence** enabled Hello World).
*   `ui.config`: OSGi configurations (Pre-configured for `jcr` persistence mode).
*   `ui.content`: Sample "Digital Twin" assets.
*   `it.tests`: integration-test module (requires a configured AEM instance).

## 🛠️ Configuration

The storage mode can be toggled via OSGi (`GraphStorageConfig`):
*   `jcr`: persistence in `/var/semanticdam/graph`.
*   `in-memory`: transient graph for local development and tests.
*   `sparql`: experimental external adapter; do not use as a production read path yet.

## Current limitations

* The JCR adapter is a reference implementation and needs an explicit migration/versioning strategy before existing graph data is changed in production.
* The public `/bin` endpoints are demonstration APIs. Add AEM authorization, dispatcher rules, rate limits, and publish-cache review before exposing them externally.
* SPARQL result parsing, remote predicate search, and remote related-asset traversal are not complete.
* No connector currently performs authenticated PIM or Workfront ingestion; supply data through AEM metadata or implement an adapter.
* Run the full build in an environment where the frontend npm cache is writable; the core module can be verified independently with `mvn -B -pl core test`.

---
*Built with SemanticDAM Core v1.0.0*
