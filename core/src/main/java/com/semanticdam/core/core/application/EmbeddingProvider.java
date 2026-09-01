package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.EmbeddingVector;

/** Provider-neutral seam for a text or multimodal embedding service. */
public interface EmbeddingProvider {
    EmbeddingVector embed(String input);
}
