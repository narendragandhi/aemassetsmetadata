package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AiEnrichmentProposal;
import java.util.Collection;

final class AiProposalJson {
    private AiProposalJson() { }

    static String list(Collection<AiEnrichmentProposal> proposals) {
        StringBuilder json = new StringBuilder("{\"proposals\":[");
        boolean first = true;
        for (AiEnrichmentProposal proposal : proposals) {
            if (!first) json.append(',');
            json.append(one(proposal));
            first = false;
        }
        return json.append("]}").toString();
    }

    static String one(AiEnrichmentProposal proposal) {
        return "{\"assetId\":\"" + e(proposal.assetId().path())
                + "\",\"predicate\":\"" + e(proposal.statement().predicateUri())
                + "\",\"object\":\"" + e(proposal.statement().objectValue())
                + "\",\"objectIsUri\":" + proposal.statement().objectIsUri()
                + ",\"source\":\"" + e(proposal.statement().source())
                + "\",\"confidence\":" + proposal.statement().confidenceScore()
                + ",\"status\":\"" + proposal.status().name()
                + "\",\"modelId\":\"" + e(proposal.modelId())
                + "\",\"promptVersion\":\"" + e(proposal.promptVersion())
                + "\",\"rationale\":\"" + e(proposal.rationale()) + "\"}";
    }

    private static String e(String value) { return JsonEscaper.escape(value); }
}
