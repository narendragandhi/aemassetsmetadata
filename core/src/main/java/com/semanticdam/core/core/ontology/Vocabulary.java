package com.semanticdam.core.core.ontology;

/**
 * IRIs that Java code needs by name. The ontology file is the source of truth:
 * {@code VocabularyTest} fails if a term here is not declared in it.
 */
public final class Vocabulary {
    public static final String SDAM = "https://semanticdam.com/ontology/core#";
    public static final String SCHEMA = "https://schema.org/";
    public static final String DCTERMS = "http://purl.org/dc/terms/";
    public static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
    public static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";
    public static final String OWL = "http://www.w3.org/2002/07/owl#";
    public static final String SH = "http://www.w3.org/ns/shacl#";
    public static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    public static final String PROV = "http://www.w3.org/ns/prov#";
    /** Namespace of PROV agents: source systems, the inference engine and AI models. */
    public static final String AGENT_NAMESPACE = "https://semanticdam.com/id/agent/";

    public static final String ASSET = SDAM + "Asset";
    public static final String BRAND_CLASS = SCHEMA + "Brand";
    public static final String PRODUCT_CLASS = SCHEMA + "Product";
    public static final String CAMPAIGN_CLASS = SDAM + "Campaign";
    public static final String CONCEPT_CLASS = SKOS + "Concept";
    public static final String CONCEPT_SCHEME_CLASS = SKOS + "ConceptScheme";

    public static final String TITLE = DCTERMS + "title";
    public static final String DESCRIPTION = DCTERMS + "description";
    public static final String FORMAT = DCTERMS + "format";
    public static final String SUBJECT = DCTERMS + "subject";
    public static final String BROADER_SUBJECT = SDAM + "broaderSubject";
    public static final String SKU = SCHEMA + "sku";
    public static final String BRAND = SCHEMA + "brand";
    public static final String DEPICTS_PRODUCT = SDAM + "depictsProduct";
    public static final String PART_OF_CAMPAIGN = SDAM + "partOfCampaign";
    public static final String PREF_LABEL = SKOS + "prefLabel";

    /** Source label attached to statements derived by the inference step. */
    public static final String INFERENCE_SOURCE = "Inference-Engine";
    /** Prefix of the source label attached to reviewed AI statements. */
    public static final String AI_SOURCE_PREFIX = "AI:";

    /** The PROV agent IRI for a statement source label ("PIM", "AI:model-x", ...). */
    public static String agentIri(String sourceLabel) {
        return AGENT_NAMESPACE + java.net.URLEncoder.encode(sourceLabel, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    /** The source label for a PROV agent IRI; the inverse of {@link #agentIri(String)}. */
    public static String sourceLabel(String agentIri) {
        if (agentIri == null || !agentIri.startsWith(AGENT_NAMESPACE)) {
            return agentIri;
        }
        return java.net.URLDecoder.decode(agentIri.substring(AGENT_NAMESPACE.length()).replace("+", "%2B"),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private Vocabulary() {
    }
}
