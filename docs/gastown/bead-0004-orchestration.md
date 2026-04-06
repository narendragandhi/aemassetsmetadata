# Gastown Orchestration: Bead 0004

## Goal
Implement Drupal-inspired "Opinionated Entity" patterns using Gastown roles.

## Assignment Table

| Role | Agent Assignment | Focus Area |
| :--- | :--- | :--- |
| **Spec Agent** | `generalist` | Define the "Entity Bundles" and "Taxonomy Integrity" rules. |
| **Domain Agent** | `codebase_investigator` | Design the `OntologyEnforcementService` logic. |
| **Graph Agent** | `generalist` | Upgrade `OntologyRegistry` to handle entity reference validation. |
| **AEM Adapter Agent** | `generalist` | Implement the "Semantic View Service" for HTL consumption. |
| **Test Agent** | `generalist` | Verify that "unauthorized" triples are rejected and "Views" return correct entities. |

## Execution Sequence
1. **Definition Phase**: (Turns 1-3) Map the "Content Types" (Bundles) for Assets.
2. **Enforcement Phase**: (Turns 4-7) Build the `OntologyEnforcementService`.
3. **Query Phase**: (Turns 8-11) Build the "Views" service for AEM components.
4. **Final Integration**: (Turns 12+) World-class "Masterpiece" validation.
