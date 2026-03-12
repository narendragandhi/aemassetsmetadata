# Bead 0007 - OSGi Wiring and Listener

## Objective

Wire the pipeline as OSGi services and add a listener that triggers ingestion on DAM asset changes.

## Tasks

- Register the pipeline as an OSGi service.
- Register an AEM metadata source service using a service user.
- Add a resource change listener for `/content/dam`.
- Provide unit tests for path normalization.

## Acceptance Criteria

- Listener triggers ingestion for asset and metadata node paths.
- Pipeline remains decoupled from AEM APIs.
- Service user configuration is documented.
