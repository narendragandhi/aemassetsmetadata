package com.semanticdam.core.core.application;

import com.google.gson.JsonObject;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Session;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * POST /bin/aemassets/reindex?path=/content/dam/folder&limit=1000 - queues an ingest job for every
 * asset below a path. Used for the initial backfill, after an ontology change, and after moving a
 * folder. Restricted to users who may write to the graph root, which by default is administrators.
 *
 * <p>The walk is bounded by {@code limit}; when {@code truncated} is true, call again for the
 * sub-folders that were not reached.</p>
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_POST,
        "sling.servlet.paths=/bin/aemassets/reindex" })
public final class AssetReindexServlet extends SlingAllMethodsServlet {
    private static final long serialVersionUID = 1L;
    private static final String GRAPH_ROOT = "/var/semanticdam/graph";
    private static final int MAX_LIMIT = 10_000;

    @Reference
    private transient JobManager jobManager;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String path = request.getParameter("path");
        if (!ApiSupport.isDamPath(path + "/")) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "path must be below /content/dam");
            return;
        }
        if (!ApiSupport.hasPermission(request, GRAPH_ROOT, Session.ACTION_ADD_NODE)) {
            response.sendError(SlingHttpServletResponse.SC_FORBIDDEN, "Write access to " + GRAPH_ROOT + " is required");
            return;
        }
        Resource root = request.getResourceResolver().getResource(path);
        if (root == null) {
            response.sendError(SlingHttpServletResponse.SC_NOT_FOUND, "Path not found");
            return;
        }
        int limit = ApiSupport.boundedInt(request.getParameter("limit"), 1000, MAX_LIMIT);
        int queued = 0;
        boolean truncated = false;
        Deque<Resource> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Resource current = pending.poll();
            if (current.isResourceType("dam:Asset")) {
                if (queued >= limit) {
                    truncated = true;
                    break;
                }
                if (jobManager.addJob(AssetIngestJobs.TOPIC, Map.of(
                        AssetIngestJobs.PROPERTY_PATH, current.getPath(),
                        AssetIngestJobs.PROPERTY_ACTION, AssetIngestJobs.ACTION_INGEST)) != null) {
                    queued++;
                }
                continue;
            }
            for (Resource child : current.getChildren()) {
                if (!"jcr:content".equals(child.getName())) {
                    pending.add(child);
                }
            }
        }
        JsonObject body = new JsonObject();
        body.addProperty("path", path);
        body.addProperty("queued", queued);
        body.addProperty("truncated", truncated);
        ApiSupport.json(response, body.toString());
    }
}
