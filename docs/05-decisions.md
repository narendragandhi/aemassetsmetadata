# Decisions

## D01 - Minimal Ontology Parsing

We will parse a limited subset of Turtle to enable early ontology lookups without adding RDF libraries. This avoids heavy dependencies while we validate the model. The registry will be replaced or enhanced once the ontology stabilizes.

## D02 - Ontology as Code

The ontology lives in the repo under `core/src/main/resources/ontology` so it can be versioned and tested alongside domain logic.
