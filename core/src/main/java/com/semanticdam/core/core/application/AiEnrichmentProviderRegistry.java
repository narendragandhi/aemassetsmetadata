package com.semanticdam.core.core.application;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Explicit provider selection independent of OSGi service ranking. */
public final class AiEnrichmentProviderRegistry {
    private final Map<String, AiEnrichmentProvider> providers;

    public AiEnrichmentProviderRegistry(Collection<AiEnrichmentProvider> providers) {
        if (providers == null || providers.stream().anyMatch(provider -> provider == null
                || provider.providerId() == null || provider.providerId().isBlank())) {
            throw new IllegalArgumentException("providers with stable ids must be provided");
        }
        try {
            this.providers = Map.copyOf(providers.stream().collect(Collectors.toMap(
                    AiEnrichmentProvider::providerId, Function.identity())));
        } catch (IllegalStateException ex) {
            throw new IllegalArgumentException("provider ids must be unique", ex);
        }
    }

    public AiEnrichmentProvider get(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must be provided");
        }
        AiEnrichmentProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("no AI provider configured for " + providerId);
        }
        return provider;
    }
}
