package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import com.semanticdam.core.core.domain.MetadataStatement;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Deterministic exact-match evaluator for provider and prompt regression fixtures. */
public class AiProposalEvaluator {
    public EvaluationSummary evaluate(Collection<AiEnrichmentProposal> proposals,
                                      Collection<MetadataStatement> expected) {
        Set<Fact> expectedFacts = expected.stream().map(Fact::of).collect(java.util.stream.Collectors.toSet());
        Set<Fact> proposedFacts = proposals.stream().map(AiEnrichmentProposal::statement)
                .map(Fact::of).collect(java.util.stream.Collectors.toSet());
        Set<Fact> truePositives = new HashSet<>(proposedFacts);
        truePositives.retainAll(expectedFacts);
        Set<Fact> falsePositives = new HashSet<>(proposedFacts);
        falsePositives.removeAll(expectedFacts);
        Set<Fact> falseNegatives = new HashSet<>(expectedFacts);
        falseNegatives.removeAll(proposedFacts);
        return new EvaluationSummary(truePositives.size(), falsePositives.size(), falseNegatives.size());
    }

    private record Fact(String predicate, String object, boolean uri) {
        static Fact of(MetadataStatement statement) {
            return new Fact(statement.predicateUri(), statement.objectValue(), statement.objectIsUri());
        }
    }

    public record EvaluationSummary(int truePositives, int falsePositives, int falseNegatives) {
        public double precision() {
            return truePositives + falsePositives == 0 ? 1.0 : (double) truePositives / (truePositives + falsePositives);
        }

        public double recall() {
            return truePositives + falseNegatives == 0 ? 1.0 : (double) truePositives / (truePositives + falseNegatives);
        }
    }
}
