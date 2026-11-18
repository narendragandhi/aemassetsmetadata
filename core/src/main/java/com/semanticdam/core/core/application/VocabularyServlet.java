package com.semanticdam.core.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.Entity;
import com.semanticdam.core.core.domain.OntologyTerm;
import com.semanticdam.core.core.graph.OntologyRegistry;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Vocabulary stewardship.
 * <ul>
 *   <li>{@code GET /bin/aemassets/vocabulary/candidates.json?class=Brand} lists candidate entries
 *       with the number of (readable) assets using each;</li>
 *   <li>{@code POST /bin/aemassets/vocabulary/review} with {@code entity}, {@code decision}
 *       (approve|reject|merge), {@code target} for a merge, and {@code reason}.</li>
 * </ul>
 * Both require membership of {@code semanticdam-vocabulary-stewards} or {@code administrators}.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.methods=" + HttpConstants.METHOD_POST,
        "sling.servlet.paths=/bin/aemassets/vocabulary/candidates.json",
        "sling.servlet.paths=/bin/aemassets/vocabulary/review" })
public final class VocabularyServlet extends SlingAllMethodsServlet {
    private static final long serialVersionUID = 1L;
    static final String STEWARDS = "semanticdam-vocabulary-stewards";

    @Reference
    private transient VocabularyStewardService stewards;

    @Reference
    private transient OntologyRegistry ontology;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        if (!request.getRequestURI().endsWith("/candidates.json")) {
            response.sendError(SlingHttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        if (!isSteward(request)) {
            response.sendError(SlingHttpServletResponse.SC_FORBIDDEN, "Vocabulary steward permission is required");
            return;
        }
        String typeUri = classParameter(request.getParameter("class"));
        Predicate<String> canRead = ApiSupport.readAccess(request);
        ApiSupport.handle(response, () -> {
            JsonArray items = new JsonArray();
            for (Entity entity : stewards.candidates(typeUri)) {
                JsonObject item = new JsonObject();
                item.addProperty("uri", entity.uri());
                item.addProperty("label", entity.label());
                item.addProperty("class", entity.typeUri());
                item.addProperty("classLabel", ontology.findByUri(entity.typeUri()).map(OntologyTerm::label).orElse(entity.typeUri()));
                JsonArray assets = new JsonArray();
                for (AssetMetadata asset : stewards.usage(entity.uri())) {
                    if (canRead.test(asset.assetId().path())) {
                        assets.add(asset.assetId().path());
                    }
                }
                item.addProperty("assetCount", assets.size());
                item.add("assets", assets);
                items.add(item);
            }
            JsonObject body = new JsonObject();
            body.addProperty("count", items.size());
            body.add("candidates", items);
            ApiSupport.json(response, body.toString());
        });
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        if (!request.getRequestURI().endsWith("/review")) {
            response.sendError(SlingHttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        if (ApiSupport.isAnonymous(request) || !isSteward(request)) {
            response.sendError(SlingHttpServletResponse.SC_FORBIDDEN, "Vocabulary steward permission is required");
            return;
        }
        String entity = request.getParameter("entity");
        String decision = request.getParameter("decision");
        String reason = request.getParameter("reason");
        if (entity == null || entity.isBlank() || decision == null || reason == null || reason.isBlank()) {
            response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, "entity, decision and reason are required");
            return;
        }
        ApiSupport.handle(response, () -> {
            VocabularyStewardService.Result result = stewards.decide(entity,
                    VocabularyStewardService.Decision.valueOf(decision.trim().toUpperCase(Locale.ROOT)),
                    request.getParameter("target"), request.getRemoteUser() + " (" + reason + ")");
            JsonObject body = new JsonObject();
            body.addProperty("uri", result.entity().uri());
            body.addProperty("label", result.entity().label());
            body.addProperty("status", result.entity().status().name());
            if (result.entity().replacedBy() != null) {
                body.addProperty("replacedBy", result.entity().replacedBy());
            }
            body.addProperty("assetsQueued", result.assetsQueued());
            ApiSupport.json(response, body.toString());
        });
    }

    private String classParameter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ontology.findByUri(value).or(() -> ontology.findByLabel(value))
                .filter(term -> term.kind() == OntologyTerm.Kind.CLASS).map(OntologyTerm::uri).orElse(value);
    }

    static boolean isSteward(SlingHttpServletRequest request) {
        String userId = request.getRemoteUser();
        if (userId == null) {
            return false;
        }
        if ("admin".equals(userId)) {
            return true;
        }
        UserManager users = request.getResourceResolver().adaptTo(UserManager.class);
        try {
            Authorizable user = users == null ? null : users.getAuthorizable(userId);
            if (user == null) {
                return false;
            }
            for (Iterator<Group> groups = user.memberOf(); groups.hasNext(); ) {
                String id = groups.next().getID();
                if (STEWARDS.equals(id) || "administrators".equals(id)) {
                    return true;
                }
            }
            return false;
        } catch (RepositoryException e) {
            return false;
        }
    }
}
