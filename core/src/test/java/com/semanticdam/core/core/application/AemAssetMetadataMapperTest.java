package com.semanticdam.core.core.application;

import com.semanticdam.core.core.TestOntology;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AemAssetMetadataMapperTest {
    private static final AssetId ASSET = AssetId.of("/content/dam/site/hero.png");
    private final AemAssetMetadataMapper mapper = new AemAssetMetadataMapper(TestOntology.load());

    @Test
    void mapsLiteralsWithSourceAndTrustFromTheOntology() {
        ExtractedAsset extracted = mapper.extract(ASSET, Map.of("dc:title", "Hero", "pim:sku", "SUM-2026"));

        MetadataStatement title = only(extracted, Vocabulary.TITLE);
        assertEquals("Hero", title.objectValue());
        assertEquals("AEM", title.source());
        assertEquals(0.75, title.confidenceScore());
        MetadataStatement sku = only(extracted, Vocabulary.SKU);
        assertEquals("PIM", sku.source());
        assertEquals(0.95, sku.confidenceScore());
        assertFalse(sku.objectIsUri());
    }

    @Test
    void linksEntitiesInsteadOfStoringStrings() {
        ExtractedAsset extracted = mapper.extract(ASSET, Map.of(
                "aem:brand", "Northwind Traders", "workfront:campaignName", "SUMMER_PROMO_2026", "pim:sku", "SUM-2026"));

        MetadataStatement brand = only(extracted, Vocabulary.BRAND);
        assertTrue(brand.objectIsUri());
        assertEquals("https://semanticdam.com/id/brand/northwind-traders", brand.objectValue());
        // authors are not the authority for brands: a new brand is a candidate for review
        Entity northwind = extracted.entities().get(brand.objectValue());
        assertEquals("Northwind Traders", northwind.label());
        assertEquals(Entity.Status.CANDIDATE, northwind.status());
        // Workfront is the authority for campaigns and PIM for products: new entries are approved
        assertEquals(Entity.Status.APPROVED, extracted.entities()
                .get("https://semanticdam.com/id/product/sum-2026").status());
        assertEquals("https://semanticdam.com/id/campaign/summer-promo-2026",
                only(extracted, Vocabulary.PART_OF_CAMPAIGN).objectValue());
        // one DAM property can feed both a literal and an entity link
        assertEquals("https://semanticdam.com/id/product/sum-2026",
                only(extracted, Vocabulary.DEPICTS_PRODUCT).objectValue());
        assertEquals(3, extracted.entities().size());
    }

    @Test
    void sameLabelInDifferentSpellingsResolvesToOneEntity() {
        String first = only(mapper.extract(ASSET, Map.of("aem:brand", "Acme")), Vocabulary.BRAND).objectValue();
        String second = only(mapper.extract(ASSET, Map.of("aem:brand", "  ACME ")), Vocabulary.BRAND).objectValue();

        assertEquals(first, second);
    }

    @Test
    void reconcilesCategoriesAgainstTheTaxonomy() {
        ExtractedAsset known = mapper.extract(ASSET, Map.of("pim:category", "Organic"));
        ExtractedAsset unknown = mapper.extract(ASSET, Map.of("pim:category", "Denim"));

        assertEquals("https://semanticdam.com/taxonomy/sustainability#OrganicCotton",
                only(known, Vocabulary.SUBJECT).objectValue());
        assertEquals("https://semanticdam.com/id/concept/denim", only(unknown, Vocabulary.SUBJECT).objectValue());
    }

    @Test
    void readsMultiValuedPropertiesAndIgnoresBlanksAndUnmappedOnes() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("dc:title", new String[] {"Hero", " ", "Hero"});
        properties.put("pim:category", List.of("Organic", "Recycled"));
        properties.put("dc:description", "");
        properties.put("xmp:CreatorTool", "Photoshop");

        ExtractedAsset extracted = mapper.extract(ASSET, properties);

        assertEquals(3, extracted.metadata().statements().size());
        assertEquals(2, extracted.metadata().statements().stream()
                .filter(statement -> statement.predicateUri().equals(Vocabulary.SUBJECT)).count());
    }

    @Test
    void mapsAssetTypeToAnOntologyClassAndIgnoresUnknownTypes() {
        assertEquals(Vocabulary.SDAM + "ProductImage",
                only(mapper.extract(ASSET, Map.of("semanticdam:assetType", "Product Image")), Vocabulary.RDF_TYPE)
                        .objectValue());
        assertTrue(mapper.extract(ASSET, Map.of("semanticdam:assetType", "Spreadsheet")).metadata().statements().isEmpty());
        // Brand is a class, but not a kind of asset
        assertTrue(mapper.extract(ASSET, Map.of("semanticdam:assetType", "Brand")).metadata().statements().isEmpty());
    }

    @Test
    void slugsAreStableAndIriSafe() {
        assertEquals("cafe-ole", AemAssetMetadataMapper.slug("Café  Olé!"));
        assertTrue(AemAssetMetadataMapper.slug("日本").matches("x[0-9a-f]+"));
    }

    @Test
    void turnsAemTagsIntoConceptsWithTheirHierarchy() {
        TagResolver tags = ids -> Map.of(
                "wknd-shared:region/emea/italy", new TagResolver.TagInfo("wknd-shared:region/emea/italy", "Italy", "wknd-shared:region/emea"),
                "wknd-shared:region/emea", new TagResolver.TagInfo("wknd-shared:region/emea", "EMEA", "wknd-shared:region"),
                "wknd-shared:region", new TagResolver.TagInfo("wknd-shared:region", "Region", null));

        ExtractedAsset extracted = mapper.extract(ASSET,
                Map.of("cq:tags", new String[] {"wknd-shared:region/emea/italy", "properties:gone/tag"}), tags);

        List<MetadataStatement> subjects = extracted.metadata().statements().stream()
                .filter(statement -> statement.predicateUri().equals(Vocabulary.SUBJECT)).toList();
        assertEquals(List.of("https://semanticdam.com/id/tag/wknd-shared/region/emea/italy",
                "https://semanticdam.com/id/tag/properties/gone/tag"),
                subjects.stream().map(MetadataStatement::objectValue).toList());
        Entity italy = extracted.entities().get("https://semanticdam.com/id/tag/wknd-shared/region/emea/italy");
        assertEquals("Italy", italy.label());
        assertEquals(Vocabulary.CONCEPT_CLASS, italy.typeUri());
        assertEquals(List.of("https://semanticdam.com/id/tag/wknd-shared/region/emea"), italy.broader());
        assertEquals("Region", extracted.entities().get("https://semanticdam.com/id/tag/wknd-shared/region").label());
        // a tag that no longer exists keeps its ID as label and has no parent
        Entity gone = extracted.entities().get("https://semanticdam.com/id/tag/properties/gone/tag");
        assertEquals("properties:gone/tag", gone.label());
        assertTrue(gone.broader().isEmpty());
        // two tag namespaces become two concept schemes
        assertEquals("https://semanticdam.com/id/tag/wknd-shared", italy.scheme());
        assertEquals(Vocabulary.CONCEPT_SCHEME_CLASS,
                extracted.entities().get("https://semanticdam.com/id/tag/wknd-shared").typeUri());
        assertEquals(List.of("wknd-shared:region/emea/italy"), italy.hiddenLabels());
        assertEquals(6, extracted.entities().size());
    }

    @Test
    void resolvesThroughAlternativeAndHiddenLabelsAndFollowsMerges() {
        // alternative and hidden labels of the governed taxonomy
        assertEquals("https://semanticdam.com/taxonomy/sustainability#OrganicCotton",
                only(mapper.extract(ASSET, Map.of("pim:category", "Bio Cotton")), Vocabulary.SUBJECT).objectValue());
        assertEquals("https://semanticdam.com/taxonomy/sustainability#RecycledPolyester",
                only(mapper.extract(ASSET, Map.of("pim:category", "rPET")), Vocabulary.SUBJECT).objectValue());

        // a stored entry merged by a steward resolves to its replacement
        Entity acme = new Entity("https://semanticdam.com/id/brand/acme", Vocabulary.BRAND_CLASS, "Acme");
        Entity typo = new Entity("https://semanticdam.com/id/brand/acmee", Vocabulary.BRAND_CLASS, "Acmee")
                .withStatus(Entity.Status.MERGED, acme.uri());
        Map<String, Entity> stored = Map.of(acme.uri(), acme, typo.uri(), typo);
        ExtractedAsset extracted = mapper.extract(ASSET, Map.of("aem:brand", "Acmee"), TagResolver.NONE,
                uri -> java.util.Optional.ofNullable(stored.get(uri)));

        assertEquals(acme.uri(), only(extracted, Vocabulary.BRAND).objectValue());
        assertEquals(Entity.Status.APPROVED, extracted.entities().get(acme.uri()).status());
    }

    @Test
    void keepsAStewardsDecisionForAStoredEntry() {
        Entity rejected = new Entity("https://semanticdam.com/id/brand/acme", Vocabulary.BRAND_CLASS, "Acme")
                .withStatus(Entity.Status.REJECTED, null);

        ExtractedAsset extracted = mapper.extract(ASSET, Map.of("aem:brand", "Acme"), TagResolver.NONE,
                uri -> uri.equals(rejected.uri()) ? java.util.Optional.of(rejected) : java.util.Optional.empty());

        assertEquals(Entity.Status.REJECTED, extracted.entities().get(rejected.uri()).status());
    }

    private static MetadataStatement only(ExtractedAsset extracted, String predicate) {
        List<MetadataStatement> matches = extracted.metadata().statements().stream()
                .filter(statement -> statement.predicateUri().equals(predicate)).toList();
        assertEquals(1, matches.size(), "statements for " + predicate);
        return matches.get(0);
    }
}
