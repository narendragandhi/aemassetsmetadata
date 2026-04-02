# Bead 0014 - Config Package

## Objective

Provide OSGi configuration and repo-init for service user mapping and ACLs.

## Tasks

- Add service user mapping config for `semanticdam` subservice.
- Add repo-init script to create service user and grant read access to DAM.
- Document configuration in operations guide.

## Acceptance Criteria

- OSGi config exists under `ui.config`.
- Repo-init creates service user and ACLs.
