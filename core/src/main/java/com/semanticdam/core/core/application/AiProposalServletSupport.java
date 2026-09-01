package com.semanticdam.core.core.application;

import org.apache.sling.api.SlingHttpServletResponse;
import java.io.IOException;

final class AiProposalServletSupport {
    private AiProposalServletSupport() { }

    static boolean isDamPath(String path) {
        return path != null && path.startsWith("/content/dam/")
                && !path.contains("/../") && !path.endsWith("/..") && !path.contains("\\");
    }

    static void json(SlingHttpServletResponse response, String body) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(body);
    }

    static void badRequest(SlingHttpServletResponse response, String message) throws IOException {
        response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, message);
    }
}
