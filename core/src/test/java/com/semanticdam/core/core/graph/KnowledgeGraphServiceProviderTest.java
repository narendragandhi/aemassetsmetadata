package com.semanticdam.core.core.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeGraphServiceProviderTest {
    @Test
    void defaultsToInMemory() {
        KnowledgeGraphServiceProvider provider = new KnowledgeGraphServiceProvider();
        provider.activate(new GraphStorageConfig() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return GraphStorageConfig.class;
            }

            @Override
            public String mode() {
                return "in-memory";
            }
        });

        provider.writeAssetMetadata(com.semanticdam.core.core.domain.AssetMetadata.empty(
                com.semanticdam.core.core.domain.AssetId.of("/content/dam/site/hero.png")));

        assertTrue(provider.findByAssetId(
                com.semanticdam.core.core.domain.AssetId.of("/content/dam/site/hero.png")).isPresent());
    }
}
