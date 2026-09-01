package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.GroundedAssetContext;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiEnrichmentProviderRegistryTest {
    @Test
    void selectsByExplicitProviderId() {
        AiEnrichmentProvider provider = new AiEnrichmentProvider() {
            @Override public String providerId() { return "internal-gateway"; }
            @Override public Collection<AiEnrichmentProposal> propose(GroundedAssetContext context) { return List.of(); }
        };

        assertSame(provider, new AiEnrichmentProviderRegistry(List.of(provider)).get("internal-gateway"));
        assertThrows(IllegalArgumentException.class,
                () -> new AiEnrichmentProviderRegistry(List.of(provider)).get("missing"));
    }
}
