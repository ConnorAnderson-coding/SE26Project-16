package com.example.demo.service;

import com.example.demo.config.ElasticsearchConfig.ElasticsearchProperties;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.dto.response.SemanticSearchResponse;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivitySearchService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Optional<RestClient> restClient;
    private final ElasticsearchProperties properties;
    private final ActivityEmbeddingService embeddingService;
    private final ActivityQueryExpansionService queryExpansionService;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SemanticSearchResponse search(String query, int size) {
        int safeSize = Math.max(1, Math.min(size, 50));
        if (!isElasticsearchReady() || !StringUtils.hasText(query)) {
            return fallbackSearch(query, safeSize);
        }

        try {
            ensureIndex();
            String expandedQuery = queryExpansionService.expand(query);
            List<Long> vectorIds = vectorSearch(expandedQuery, safeSize * 3);
            List<Long> keywordIds = keywordSearch(query, expandedQuery, safeSize * 3);
            List<Long> rankedIds = rrf(vectorIds, keywordIds, properties.getRrfRankConstant(), safeSize);
            return SemanticSearchResponse.builder()
                    .query(query)
                    .elasticsearchEnabled(true)
                    .fallback(false)
                    .results(loadActivities(rankedIds, query, expandedQuery))
                    .build();
        } catch (IOException ex) {
            return fallbackSearch(query, safeSize);
        }
    }

    @Transactional(readOnly = true)
    public int rebuildIndex() {
        if (!isElasticsearchReady()) {
            return 0;
        }
        try {
            recreateIndex();
            List<Activity> activities = activityRepository.findAll();
            for (Activity activity : activities) {
                indexActivity(activity);
            }
            refreshIndex();
            return activities.size();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to rebuild activity search index", ex);
        }
    }

    public void syncActivity(Activity activity) {
        if (!isElasticsearchReady() || activity.getId() == null) {
            return;
        }
        try {
            ensureIndex();
            indexActivity(activity);
        } catch (IOException ignored) {
            // Search indexing is best-effort and must not break the core activity workflow.
        }
    }

    public void deleteActivity(Long id) {
        if (!isElasticsearchReady()) {
            return;
        }
        try {
            Request request = new Request("DELETE", "/" + properties.getIndex() + "/_doc/" + id);
            restClient.get().performRequest(request);
        } catch (IOException ignored) {
            // Missing ES documents are safe to ignore.
        }
    }

    private SemanticSearchResponse fallbackSearch(String query, int size) {
        var page = activityRepository.search(null, "published", null,
                StringUtils.hasText(query) ? query : null,
                PageRequest.of(0, size));
        List<ActivityResponse> results = page.getContent().stream()
                .map(DtoMapper::toActivityResponse)
                .toList();
        return SemanticSearchResponse.builder()
                .query(query)
                .elasticsearchEnabled(isElasticsearchReady())
                .fallback(true)
                .results(results)
                .build();
    }

    private boolean isElasticsearchReady() {
        return properties.isEnabled() && restClient.isPresent();
    }

    private void ensureIndex() throws IOException {
        Request exists = new Request("HEAD", "/" + properties.getIndex());
        try {
            restClient.get().performRequest(exists);
        } catch (IOException ex) {
            createIndex();
        }
    }

    private void recreateIndex() throws IOException {
        try {
            restClient.get().performRequest(new Request("DELETE", "/" + properties.getIndex()));
        } catch (IOException ignored) {
            // The index may not exist on first startup.
        }
        createIndex();
    }

    private void createIndex() throws IOException {
        Map<String, Object> body = Map.of(
                "mappings", Map.of(
                        "properties", Map.of(
                                "activityId", Map.of("type", "long"),
                                "title", Map.of("type", "text", "analyzer", properties.getTextAnalyzer()),
                                "description", Map.of("type", "text", "analyzer", properties.getTextAnalyzer()),
                                "location", Map.of("type", "text", "analyzer", properties.getTextAnalyzer()),
                                "college", Map.of("type", "keyword"),
                                "category", Map.of("type", "keyword"),
                                "status", Map.of("type", "keyword"),
                                "tags", Map.of("type", "text", "analyzer", properties.getTextAnalyzer()),
                                "contentVector", Map.of(
                                        "type", "dense_vector",
                                        "dims", properties.getVectorDims(),
                                        "index", true,
                                        "similarity", "cosine"
                                )
                        )
                )
        );

        Request request = new Request("PUT", "/" + properties.getIndex());
        request.setEntity(jsonEntity(body));
        restClient.get().performRequest(request);
    }

    private void indexActivity(Activity activity) throws IOException {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("activityId", activity.getId());
        document.put("title", activity.getTitle());
        document.put("description", activity.getDescription());
        document.put("location", activity.getLocation());
        document.put("college", activity.getCollege());
        document.put("category", activity.getCategory());
        document.put("status", activity.getStatus());
        document.put("tags", activity.getTags() != null ? activity.getTags() : List.of());
        document.put("contentVector", embeddingService.embed(searchableText(activity), properties.getVectorDims()));

        Request request = new Request("PUT", "/" + properties.getIndex() + "/_doc/" + activity.getId());
        request.setEntity(jsonEntity(document));
        restClient.get().performRequest(request);
    }

    private List<Long> vectorSearch(String query, int size) throws IOException {
        Map<String, Object> body = Map.of(
                "_source", false,
                "size", size,
                "knn", Map.of(
                        "field", "contentVector",
                        "query_vector", embeddingService.embed(query, properties.getVectorDims()),
                        "k", size,
                        "num_candidates", Math.max(100, size * 5),
                        "filter", Map.of("term", Map.of("status", "published"))
                )
        );
        return searchIds(body, properties.getVectorMinScore());
    }

    private List<Long> keywordSearch(String query, String expandedQuery, int size) throws IOException {
        boolean expanded = !query.equals(expandedQuery);
        Map<String, Object> body = Map.of(
                "_source", false,
                "size", size,
                "query", Map.of(
                        "bool", Map.of(
                                "filter", List.of(Map.of("term", Map.of("status", "published"))),
                                "should", buildKeywordShouldClauses(query, expandedQuery, expanded),
                                "minimum_should_match", 1
                        )
                )
        );
        return searchIds(body);
    }

    private List<Map<String, Object>> buildKeywordShouldClauses(String query, String expandedQuery, boolean expanded) {
        List<Map<String, Object>> clauses = new ArrayList<>();
        clauses.add(Map.of("multi_match", Map.of(
                "query", query,
                "fields", List.of("title^4", "description^2", "location", "tags^3", "category"),
                "type", "best_fields",
                "minimum_should_match", expanded ? "50%" : "70%",
                "boost", 3
        )));

        if (expanded) {
            clauses.add(Map.of("multi_match", Map.of(
                    "query", expandedQuery,
                    "fields", List.of("title^3", "description^2", "location", "tags^4", "category^2"),
                    "type", "best_fields",
                    "minimum_should_match", "20%",
                    "boost", 1.8
            )));
        }
        return clauses;
    }

    private List<Long> searchIds(Map<String, Object> body) throws IOException {
        return searchIds(body, null);
    }

    private List<Long> searchIds(Map<String, Object> body, Double minScore) throws IOException {
        Request request = new Request("POST", "/" + properties.getIndex() + "/_search");
        request.setEntity(jsonEntity(body));
        Response response = restClient.get().performRequest(request);
        try (InputStream stream = response.getEntity().getContent()) {
            Map<String, Object> parsed = objectMapper.readValue(stream, MAP_TYPE);
            Map<String, Object> hits = castMap(parsed.get("hits"));
            List<Map<String, Object>> items = castList(hits.get("hits"));
            return items.stream()
                    .filter(hit -> minScore == null || score(hit) >= minScore)
                    .map(hit -> Long.valueOf(String.valueOf(hit.get("_id"))))
                    .toList();
        }
    }

    private List<Long> rrf(List<Long> vectorIds, List<Long> keywordIds, int rankConstant, int limit) {
        Map<Long, Double> scores = new HashMap<>();
        addRrfScores(scores, vectorIds, rankConstant);
        addRrfScores(scores, keywordIds, rankConstant);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private void addRrfScores(Map<Long, Double> scores, List<Long> ids, int rankConstant) {
        for (int i = 0; i < ids.size(); i++) {
            scores.merge(ids.get(i), 1.0 / (rankConstant + i + 1), Double::sum);
        }
    }

    private List<ActivityResponse> loadActivities(Collection<Long> ids, String query, String expandedQuery) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Activity> byId = activityRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Activity::getId, activity -> activity));
        return ids.stream()
                .map(byId::get)
                .filter(activity -> activity != null && !"draft".equals(activity.getStatus()))
                .filter(activity -> relevanceScore(activity, query, expandedQuery) > 0)
                .map(DtoMapper::toActivityResponse)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private int relevanceScore(Activity activity, String query, String expandedQuery) {
        String content = searchableText(activity).toLowerCase(Locale.ROOT);
        int score = 0;
        if (StringUtils.hasText(query) && content.contains(query.toLowerCase(Locale.ROOT))) {
            score += 3;
        }
        for (String term : expandedQuery.split("\\s+")) {
            if (term.length() >= 2 && content.contains(term.toLowerCase(Locale.ROOT))) {
                score++;
            }
        }
        return score;
    }

    private void refreshIndex() throws IOException {
        restClient.get().performRequest(new Request("POST", "/" + properties.getIndex() + "/_refresh"));
    }

    private String searchableText(Activity activity) {
        return String.join(" ",
                nullSafe(activity.getTitle()),
                nullSafe(activity.getDescription()),
                nullSafe(activity.getLocation()),
                nullSafe(activity.getCategory()),
                activity.getTags() == null ? "" : String.join(" ", activity.getTags()));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private NStringEntity jsonEntity(Object value) throws IOException {
        return new NStringEntity(objectMapper.writeValueAsString(value), ContentType.APPLICATION_JSON);
    }

    private double score(Map<String, Object> hit) {
        Object rawScore = hit.get("_score");
        if (rawScore instanceof Number number) {
            return number.doubleValue();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
