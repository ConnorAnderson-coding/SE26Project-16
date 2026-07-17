package com.example.demo.recommend.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.get.GetResult;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.example.demo.common.BusinessException;
import com.example.demo.config.ElasticsearchProperties;
import com.example.demo.recommend.RecommendationHit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchRecommendationRepository {

    private final ElasticsearchClient elasticsearchClient;
    private final RestClient restClient;
    private final ElasticsearchProperties elasticsearchProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public float[] inferTextEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String modelId = elasticsearchProperties.getEmbeddingModelId();
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "docs", List.of(Map.of("text_field", text))));
            Request request = new Request(
                    "POST",
                    "/_ml/trained_models/" + modelId + "/_infer");
            request.setJsonEntity(body);
            Response response = restClient.performRequest(request);
            try (InputStream in = response.getEntity().getContent()) {
                JsonNode root = objectMapper.readTree(in);
                JsonNode embedding = root.path("inference_results").path(0)
                        .path("predicted_value");
                if (!embedding.isArray()) {
                    embedding = root.findValue("predicted_value");
                }
                if (embedding == null || !embedding.isArray() || embedding.isEmpty()) {
                    log.warn("GTE infer returned no embedding for text len={}", text.length());
                    return null;
                }
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                return vector;
            }
        }
        catch (IOException ex) {
            log.error("Infer embedding failed model={}", modelId, ex);
            throw new BusinessException("用户兴趣向量推理失败: " + ex.getMessage());
        }
    }

    public Map<Long, float[]> mgetEmbeddings(List<Long> activityIds) {
        Map<Long, float[]> out = new HashMap<>();
        if (activityIds == null || activityIds.isEmpty()) {
            return out;
        }
        try {
            List<String> ids = activityIds.stream().map(String::valueOf).toList();
            MgetResponse<JsonData> response = elasticsearchClient.mget(m -> m
                            .index(elasticsearchProperties.getIndexActivities())
                            .ids(ids)
                            .sourceIncludes("activity_embedding"),
                    JsonData.class);
            for (MultiGetResponseItem<JsonData> item : response.docs()) {
                if (item.isFailure() || item.result() == null || !item.result().found()) {
                    continue;
                }
                GetResult<JsonData> doc = item.result();
                Long id = parseLong(doc.id());
                if (id == null || doc.source() == null) {
                    continue;
                }
                float[] embedding = extractEmbedding(doc.source());
                if (embedding != null) {
                    out.put(id, embedding);
                }
            }
            return out;
        }
        catch (IOException ex) {
            log.error("mget activity embeddings failed", ex);
            throw new BusinessException("读取活动向量失败: " + ex.getMessage());
        }
    }

    public List<RecommendationHit> knnByVector(float[] queryVector, int recallSize) {
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }
        int k = Math.max(recallSize, 1);
        int numCandidates = Math.max(k * 2, 100);
        List<Float> qv = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            qv.add(v);
        }
        try {
            KnnSearch knn = KnnSearch.of(b -> b
                    .field("activity_embedding")
                    .k(k)
                    .numCandidates(numCandidates)
                    .queryVector(qv)
                    .filter(Query.of(q -> q.term(t -> t.field("status").value("published")))));
            SearchRequest request = new SearchRequest.Builder()
                    .index(elasticsearchProperties.getIndexActivities())
                    .size(k)
                    .knn(knn)
                    .source(s -> s.fetch(false))
                    .build();
            SearchResponse<JsonData> response = elasticsearchClient.search(request, JsonData.class);
            List<RecommendationHit> hits = new ArrayList<>();
            for (Hit<JsonData> hit : response.hits().hits()) {
                Long id = parseLong(hit.id());
                if (id == null) {
                    continue;
                }
                double score = hit.score() != null ? hit.score() : 0.0;
                hits.add(new RecommendationHit(id, score));
            }
            return hits;
        }
        catch (IOException ex) {
            log.error("recommend knn failed", ex);
            throw new BusinessException("推荐向量召回失败: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private float[] extractEmbedding(JsonData source) {
        try {
            Map<String, Object> map = source.to(Map.class);
            Object embObj = map.get("activity_embedding");
            if (!(embObj instanceof List<?> list) || list.isEmpty()) {
                return null;
            }
            float[] vector = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object n = list.get(i);
                vector[i] = n instanceof Number num ? num.floatValue() : Float.parseFloat(String.valueOf(n));
            }
            return vector;
        }
        catch (RuntimeException ex) {
            return null;
        }
    }

    private static Long parseLong(String id) {
        if (id == null) {
            return null;
        }
        try {
            return Long.parseLong(id);
        }
        catch (NumberFormatException ex) {
            return null;
        }
    }
}
