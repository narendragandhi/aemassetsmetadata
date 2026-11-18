package com.semanticdam.core.core.application;

import com.semanticdam.core.core.graph.GraphStoreException;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Predicate;

/** Shared request handling for the {@code /bin/aemassets} endpoints. */
final class ApiSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ApiSupport.class);

    @FunctionalInterface
    interface Action {
        void run() throws IOException;
    }

    private ApiSupport() {
    }

    static boolean isDamPath(String path) {
        return path != null && path.startsWith("/content/dam/") && !path.contains("/../")
                && !path.endsWith("/..") && !path.contains("\\") && !path.contains("//");
    }

    /**
     * The graph is read with a service user, so each endpoint must check the caller's own access.
     * An asset the caller cannot read is indistinguishable from one that does not exist.
     */
    static Predicate<String> readAccess(SlingHttpServletRequest request) {
        return path -> isDamPath(path) && request.getResourceResolver().getResource(path) != null;
    }

    /**
     * Resolves the {@code path} parameter to an asset the caller may read, or answers 400/404.
     */
    static Optional<String> readableAssetPath(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {
        String path = request.getParameter("path");
        if (!isDamPath(path)) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "Missing or invalid path parameter");
            return Optional.empty();
        }
        if (!readAccess(request).test(path)) {
            response.sendError(SlingHttpServletResponse.SC_NOT_FOUND, "Asset not found");
            return Optional.empty();
        }
        return Optional.of(path);
    }

    /** Whether the caller may change the asset's metadata, the bar for proposing and reviewing AI facts. */
    static boolean canEditMetadata(SlingHttpServletRequest request, String assetPath) {
        return hasPermission(request, assetPath + "/jcr:content/metadata", Session.ACTION_SET_PROPERTY);
    }

    static boolean hasPermission(SlingHttpServletRequest request, String path, String action) {
        Session session = request.getResourceResolver().adaptTo(Session.class);
        try {
            return session != null && session.hasPermission(path, action);
        } catch (RepositoryException e) {
            return false;
        }
    }

    static boolean isAnonymous(SlingHttpServletRequest request) {
        String user = request.getRemoteUser();
        return user == null || user.isBlank() || "anonymous".equalsIgnoreCase(user);
    }

    static void json(SlingHttpServletResponse response, String body) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(body);
    }

    static int boundedInt(String raw, int fallback, int max) {
        try {
            return raw == null ? fallback : Math.max(1, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Maps domain failures to HTTP statuses instead of leaking stack traces as 500s. */
    static void handle(SlingHttpServletResponse response, Action action) throws IOException {
        try {
            action.run();
        } catch (GraphStoreException e) {
            LOG.error("Graph store is unavailable", e);
            response.sendError(SlingHttpServletResponse.SC_SERVICE_UNAVAILABLE, "Graph store is unavailable");
        } catch (IllegalArgumentException e) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            LOG.warn("Request could not be completed: {}", e.getMessage());
            response.sendError(SlingHttpServletResponse.SC_CONFLICT, e.getMessage());
        }
    }
}
