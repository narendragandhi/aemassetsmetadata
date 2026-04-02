# Architecture: Multi-Source Metadata Hub

This document defines the architectural patterns and components used to manage asset metadata across a distributed enterprise landscape (PIM, Workfront, AEM, AJO).

## 1. High-Level Vision
The architecture transforms AEM from a simple asset store into a **Semantic Metadata Hub**. Instead of storing disconnected strings, AEM manages a **Knowledge Graph** where every fact has a defined meaning (Ontology) and a clear origin (Provenance).

## 2. Component Layers

### A. Ingestion Layer (The Landing Zone)
*   **Sources**: External systems like **PIM** and **Workfront** push data into AEM via standard APIs or Workflows.
*   **Storage**: Raw data initially lands in JCR properties under `jcr:content/metadata` using system-specific namespaces (e.g., `pim:sku`, `workfront:projectID`).
*   **Trigger**: The `AssetMetadataResourceChangeListener` detects these changes and initiates the normalization pipeline.

### B. Normalization Layer (The Semantic Brain)
*   **Mapper (`AemAssetMetadataMapper`)**: This core component acts as the "translator." It performs two critical functions:
    1.  **Semantic Mapping**: Translates system-specific keys to canonical **Ontology Predicates** (e.g., `pim:sku` -> `aem:sku`).
    2.  **Provenance Tagging**: Attaches a `source` attribute to every statement (e.g., "PIM", "Workfront", "AEM").
*   **Ontology Registry**: A central store (based on `asset-ontology.ttl`) that defines the "Source of Truth" for what each metadata field means.

### C. Knowledge Graph Layer (The Fact Store)
*   **Domain Model**: Metadata is stored as `MetadataStatement` objects (Triples: Subject, Predicate, Object + Source).
*   **Storage Adapter**: Decouples the domain logic from the physical database.
    *   *In-Memory*: Used for rapid local development and testing.
    *   *SPARQL (Future)*: Architected to target enterprise RDF stores (GraphDB, Stardog) for large-scale relationship queries.

### D. Consumption Layer (The Delivery Zone)
*   **API Endpoints**: Normalized views are exposed via `/bin/aemassets/metadata.json` and `.ttl`.
*   **Consumer (AJO)**: **Adobe Journey Optimizer** consumes the normalized JSON. Because the data is normalized, AJO can use a stable schema for personalization, regardless of which upstream system provided the data.

## 3. Data Flow: PIM/Workfront to AJO

1.  **PIM** updates `pim:sku` on an asset.
2.  **Workfront** updates `workfront:campaignName` on the same asset.
3.  **AEM Listener** triggers the `AssetMetadataPipelineService`.
4.  **Mapper** creates two statements:
    *   Fact A: `aem:sku` = "SUM-2026" (Source: PIM)
    *   Fact B: `aem:belongsToCampaign` = "Summit 26" (Source: Workfront)
5.  **Knowledge Graph** indexes these facts.
6.  **AJO** calls the JSON API and receives a clean, unified record for its journey logic.

## 4. Provenance & Governance
The inclusion of the `source` field in every statement allows for advanced governance:
*   **Lineage**: Architects can audit exactly where a piece of data came from.
*   **Trust Levels**: Future implementations can use the source to resolve conflicts (e.g., "If PIM and AEM disagree on a brand name, trust PIM").

## 5. EA Strategic Alignment
*   **Decoupling**: Upstream schemas can change without breaking downstream consumers.
*   **Interoperability**: Uses W3C standards (RDF, Turtle) for data portability.
*   **Scalability**: The "Adapter" pattern allows for a seamless transition from JCR storage to a dedicated Graph Database.
