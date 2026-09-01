package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.MetadataStatement;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class JcrAiProposalStoreTest {
    @Test
    void roundTripsProposalStatusAndModelMetadata(AemContext context) throws Exception {
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(context.resourceResolver());
        JcrAiProposalStore store = new JcrAiProposalStore(factory);
        AssetId assetId = AssetId.of("/content/dam/site/hero.png");
        AiEnrichmentProposal proposal = AiEnrichmentProposal.proposed(assetId,
                MetadataStatement.literal("https://example.com/predicate/brand", "Example", "vision", true, 0.9),
                "vision-model", "prompt-7", "logo evidence").approve();

        store.save(proposal);

        AiEnrichmentProposal actual = store.findByAsset(assetId).get(0);
        assertEquals(proposal, actual);
        assertEquals("vision-model", actual.modelId());
        assertEquals(AiEnrichmentProposal.Status.APPROVED, actual.status());
    }
}
