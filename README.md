# AEM Assets Metadata Knowledge Graph

This repository is an AEM implementation for knowledge-graph-backed asset metadata. It is built from the latest AEM Project Archetype and targets Java 21. The focus is on DDD boundaries, ontology-driven metadata, and spec-driven delivery.

## Quick Start

1. Build everything:

```bash
mvn clean install
```

1. Deploy the full package to a local author:

```bash
mvn clean install -PautoInstallSinglePackage
```

1. Run unit tests:

```bash
mvn clean test
```

## Project Modules

- `core` Java bundle with domain logic and services
- `ui.apps` /apps content package with components and clientlibs
- `ui.content` content package with sample content
- `ui.config` runmode-specific OSGi configuration
- `all` combined content package
- `it.tests` integration tests
- `ui.tests` Cypress UI tests
- `dispatcher` dispatcher configuration

## Demo

The content package includes a sample DAM asset at `/content/dam/aemassetsmetadata/asset.jpg` with metadata mapped by the pipeline. After deploying, run:

```bash
bash demo/curl-demo.sh
```

## Documentation Map

- `docs/01-vision.md` problem statement and goals
- `docs/02-architecture.md` DDD boundaries and knowledge graph architecture
- `docs/03-bmad-spec.md` BMAD spec-driven workflow template
- `docs/04-ubiquitous-language.md` domain vocabulary
- `docs/05-decisions.md` architecture decisions
- `docs/06-operations.md` operations guide
- `docs/07-show-and-tell.md` demo flows and expected output
- `docs/beads/0001-foundation.md` initial task beads
- `docs/gastown/agent-orchestration.md` agent orchestration template
- `docs/LOG.md` execution log for this repository

## External Metadata Ingestion

When metadata originates outside AEM, the repo provides a normalization and governance layer:

1. External system populates asset properties in AEM (via API, workflow, or integration).
1. The extraction pipeline maps raw fields to ontology terms.
1. The knowledge graph stores normalized triples that can be extended with provenance.
1. Consumers query consistent semantics across mixed sources, regardless of upstream field shape.

## Java 21

The build enforces Java 21 at the Maven root. Update your local JDK accordingly before running builds.
