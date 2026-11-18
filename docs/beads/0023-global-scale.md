# Bead 0023 - Global Scale & Visualization

## Objective
Enable infinite scalability by connecting to external Triplestores and provide world-class visual "Graph Explorer" for AEM authors.

## Tasks
- **External SPARQL Connector**: Implement `SparqlRemoteKnowledgeGraphStore` to allow the system to use AWS Neptune, GraphDB, or Apache Jena as the primary brain.
- **Real-time Graph Sync**: Implement `ExternalGraphSyncService` to replicate JCR triples to the remote Triplestore asynchronously.
- **The Graph Explorer (UI)**: Build a custom Asset Side Panel using D3.js to visualize the "Semantic Web" of the current asset.
- **Path-Finding Discovery**: Implement "Shortest Path" logic in `GraphRelationshipQueryService` to answer: "How is Asset A related to Campaign B?".

## Acceptance Criteria
- `SparqlRemoteKnowledgeGraphStore` can execute SPARQL updates against a configured endpoint.
- Side-panel component renders an interactive SVG graph of asset relationships.
- System supports "Hybrid Mode" (JCR for persistence, SPARQL for high-speed discovery).
- Build succeeds with Java 21.
