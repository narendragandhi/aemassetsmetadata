# SemanticDAM Intelligence Engine (AEM)

An enterprise-grade **Semantic Asset Intelligence Engine** for AEM Cloud Service. This project transforms standard AEM Assets into a **Knowledge Graph**, enabling cross-silo metadata discovery between PIM, Workfront, and DAM domains.

## 🌟 Key "God-Tier" Features

*   **Semantic Knowledge Graph:** Treats assets as interconnected entities rather than isolated files.
*   **Predictive Relationship Discovery:** Automatically suggests related assets based on shared metadata identifiers (SKU, Brand, Campaign).
*   **JCR-Backed Persistence:** A custom `JcrKnowledgeGraphStore` ensures the "brain" of the system survives restarts and is fully integrated into the AEM repository.
*   **Enterprise Integration Ready:** Pre-built mapping for **PIM** (Product Information) and **Workfront** (Workflow/Project) data silos.
*   **Java 21 & AEM CS Native:** Optimized for the latest AEM Cloud Service architecture.

## 🚀 Quick Start

1. **Build the Engine:**
   ```bash
   mvn clean install -DskipTests
   ```

2. **Deploy to AEM Author:**
   ```bash
   mvn clean install -PautoInstallSinglePackage
   ```

3. **Verify Intelligence:**
   Access the semantic suggestion API for any asset:
   ```bash
   curl -u admin:admin "http://localhost:4502/bin/aemassets/suggestions.json?path=/content/dam/semanticdam/asset.jpg"
   ```

## 🏗️ Core Architecture

The system uses a **Decoupled Metadata Pipeline**:
1.  **Extraction:** Maps raw system properties (e.g., `pim:sku`) to normalized ontology terms.
2.  **Governance:** Resolves conflicts between multiple data sources.
3.  **Indexing:** Stores triples in the **JCR Knowledge Graph**.
4.  **Discovery:** Traverses the graph to find hidden relationships between assets.

## 📂 Project Structure

*   `core`: The Java "Brain" (Services, Domain Models, Graph Stores).
*   `ui.apps`: AEM Components (including the **Semantic Intelligence** enabled Hello World).
*   `ui.config`: OSGi configurations (Pre-configured for `jcr` persistence mode).
*   `ui.content`: Sample "Digital Twin" assets.
*   `it.tests`: Integration tests for graph traversal and relationship discovery.

## 🛠️ Configuration

The storage mode can be toggled via OSGi (`GraphStorageConfig`):
*   `jcr` (Default): Permanent persistence in `/var/semanticdam/graph`.
*   `in-memory`: High-speed transient graph for testing.
*   `sparql`: Readiness for external Triplestores (AWS Neptune, GraphDB).

---
*Built with SemanticDAM Core v1.0.0*
