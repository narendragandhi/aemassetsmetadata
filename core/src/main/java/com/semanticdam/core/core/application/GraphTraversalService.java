package com.semanticdam.core.core.application;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.domain.AssetMetadata;
import com.semanticdam.core.core.domain.MetadataStatement;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import com.semanticdam.core.core.ontology.Vocabulary;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Walks the asset graph. Assets are connected through the entity nodes they share
 * (asset -> brand -> asset), so every hop between two assets passes through an entity.
 */
@Component(service = GraphTraversalService.class)
public class GraphTraversalService {
    /** Upper bound on nodes expanded by one path search, to keep a request bounded. */
    static final int MAX_EXPANDED_NODES = 500;

    /** A link from an asset to an entity. */
    public record Link(String predicateUri, String entityUri) {
    }

    /**
     * An asset one hop away, with the entities it shares with the origin and a relatedness score:
     * the sum over shared entities of {@code 1 / ln(1 + n)}, where n is the number of assets linked
     * to that entity. A shared rare entity (a product) counts far more than a shared common one.
     * Only asserted links count; derived ones (broader subjects) never make assets related.
     */
    public record RelatedAsset(AssetMetadata asset, List<Link> sharedLinks, double score) {
    }

    /** One node on a path. {@code predicateUri} is the edge that led here; null for the start node. */
    public record PathStep(String node, boolean asset, String predicateUri) {
    }

    private static final String INFERRED = Vocabulary.INFERENCE_SOURCE;
    private final KnowledgeGraphService graph;

    @Activate
    public GraphTraversalService(@Reference KnowledgeGraphService graph) {
        this.graph = graph;
    }

    /** Related assets ordered by the number of shared entities, most connected first. */
    public List<RelatedAsset> related(AssetId assetId, int limit) {
        Optional<AssetMetadata> origin = graph.findByAssetId(assetId);
        if (origin.isEmpty() || limit <= 0) {
            return List.of();
        }
        Map<AssetId, AssetMetadata> assets = new LinkedHashMap<>();
        Map<AssetId, List<Link>> links = new LinkedHashMap<>();
        Map<AssetId, Double> scores = new HashMap<>();
        Map<String, Double> weights = new HashMap<>();
        for (Link link : entityLinks(origin.get())) {
            java.util.Collection<AssetMetadata> referencing = graph.findReferencing(link.entityUri());
            double weight = weights.computeIfAbsent(link.entityUri(),
                    key -> 1.0 / Math.log(1.0 + Math.max(1, referencing.size())));
            for (AssetMetadata other : referencing) {
                if (!other.assetId().equals(assetId)) {
                    assets.putIfAbsent(other.assetId(), other);
                    List<Link> shared = links.computeIfAbsent(other.assetId(), key -> new ArrayList<>());
                    if (shared.stream().noneMatch(existing -> existing.entityUri().equals(link.entityUri()))) {
                        scores.merge(other.assetId(), weight, Double::sum);
                    }
                    if (!shared.contains(link)) {
                        shared.add(link);
                    }
                }
            }
        }
        return assets.values().stream()
                .map(asset -> new RelatedAsset(asset, List.copyOf(links.get(asset.assetId())),
                        scores.getOrDefault(asset.assetId(), 0.0)))
                .sorted((left, right) -> {
                    int byScore = Double.compare(right.score(), left.score());
                    return byScore != 0 ? byScore : left.asset().assetId().path().compareTo(right.asset().assetId().path());
                })
                .limit(limit)
                .toList();
    }

    /**
     * Shortest path from an asset to another asset (a {@code /content/dam} path) or to an entity IRI.
     *
     * @param maxHops maximum number of edges; an asset-to-asset connection through one entity is 2
     * @return the nodes from start to target, or empty if they are not connected within the bound
     */
    public Optional<List<PathStep>> shortestPath(AssetId from, String target, int maxHops) {
        if (from == null || target == null || target.isBlank() || maxHops <= 0
                || graph.findByAssetId(from).isEmpty()) {
            return Optional.empty();
        }
        String start = from.path();
        if (start.equals(target)) {
            return Optional.of(List.of(new PathStep(start, true, null)));
        }
        Map<String, PathStep> cameBy = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Map<String, Integer> depth = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        cameBy.put(start, new PathStep(start, true, null));
        depth.put(start, 0);
        queue.add(start);
        int expanded = 0;
        while (!queue.isEmpty() && expanded++ < MAX_EXPANDED_NODES) {
            String node = queue.poll();
            int nextDepth = depth.get(node) + 1;
            if (nextDepth > maxHops) {
                continue;
            }
            for (PathStep neighbour : neighbours(node, cameBy.get(node).asset())) {
                if (cameBy.containsKey(neighbour.node())) {
                    continue;
                }
                cameBy.put(neighbour.node(), neighbour);
                parent.put(neighbour.node(), node);
                depth.put(neighbour.node(), nextDepth);
                if (neighbour.node().equals(target)) {
                    List<PathStep> path = new ArrayList<>();
                    for (String current = target; current != null; current = parent.get(current)) {
                        path.add(cameBy.get(current));
                    }
                    Collections.reverse(path);
                    return Optional.of(path);
                }
                queue.add(neighbour.node());
            }
        }
        return Optional.empty();
    }

    private List<PathStep> neighbours(String node, boolean asset) {
        List<PathStep> result = new ArrayList<>();
        if (asset) {
            graph.findByAssetId(AssetId.of(node)).ifPresent(metadata -> entityLinks(metadata)
                    .forEach(link -> result.add(new PathStep(link.entityUri(), false, link.predicateUri()))));
        } else {
            for (AssetMetadata referencing : graph.findReferencing(node)) {
                referencing.statements().stream()
                        .filter(statement -> statement.objectIsUri() && statement.objectValue().equals(node))
                        .findFirst()
                        .ifPresent(statement -> result.add(
                                new PathStep(referencing.assetId().path(), true, statement.predicateUri())));
            }
        }
        return result;
    }

    /**
     * Links used for relatedness and paths: asserted, valid links to entities. Derived links
     * (broader subjects, rule results) are left out: two assets are not related because both
     * inherit "Region" from the tag tree.
     */
    static List<Link> entityLinks(AssetMetadata metadata) {
        List<Link> links = new ArrayList<>();
        for (MetadataStatement statement : metadata.statements()) {
            if (statement.objectIsUri() && statement.isValid() && !INFERRED.equals(statement.source())
                    && !Vocabulary.RDF_TYPE.equals(statement.predicateUri())) {
                Link link = new Link(statement.predicateUri(), statement.objectValue());
                if (!links.contains(link)) {
                    links.add(link);
                }
            }
        }
        return links;
    }
}
