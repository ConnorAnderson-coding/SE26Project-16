package com.example.demo.search.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.example.demo.common.BusinessException;
import com.example.demo.config.ElasticsearchProperties;
import com.example.demo.search.ActivitySearchCriteria;
import com.example.demo.search.ActivitySearchHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchActivitySearchRepository implements ActivitySearchRepository {

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties elasticsearchProperties;

    @Override
    public List<ActivitySearchHit> searchKeyword(ActivitySearchCriteria criteria, int recallSize) {
        Query query = Query.of(q -> q.bool(buildBoolQuery(criteria, buildKeywordQuery(criteria.keyword()))));
        return executeQuerySearch(query, recallSize, "keyword");
    }

    @Override
    public List<ActivitySearchHit> searchSemantic(ActivitySearchCriteria criteria, int recallSize) {
        return executeKnnSearch(criteria, recallSize);
    }

    @Override
    public long count(ActivitySearchCriteria criteria) {
        try {
            Query query = Query.of(q -> q.bool(buildFilterOnlyQuery(criteria)));
            return elasticsearchClient.count(new CountRequest.Builder()
                    .index(elasticsearchProperties.getIndexActivities())
                    .query(query)
                    .build()).count();
        }
        catch (IOException ex) {
            throw new BusinessException("Elasticsearch 统计失败: " + ex.getMessage());
        }
    }

    @Override
    public long countAll() {
        try {
            return elasticsearchClient.count(new CountRequest.Builder()
                    .index(elasticsearchProperties.getIndexActivities())
                    .build()).count();
        }
        catch (IOException ex) {
            throw new BusinessException("Elasticsearch 统计失败: " + ex.getMessage());
        }
    }

    private List<ActivitySearchHit> executeKnnSearch(ActivitySearchCriteria criteria, int recallSize) {
        String modelId = elasticsearchProperties.getEmbeddingModelId();
        // GTE (and most non-E5 models) do not use the E5 "query: " / "passage: " prefixes.
        String modelText = criteria.keyword();
        int numCandidates = Math.max(recallSize * 2, 100);

        try {
            KnnSearch knn = KnnSearch.of(k -> {
                k.field("activity_embedding")
                        .k(recallSize)
                        .numCandidates(numCandidates)
                        .queryVectorBuilder(qvb -> qvb.textEmbedding(te -> te
                                .modelId(modelId)
                                .modelText(modelText)));
                for (Query filter : buildKnnFilters(criteria)) {
                    k.filter(filter);
                }
                return k;
            });

            SearchRequest request = new SearchRequest.Builder()
                    .index(elasticsearchProperties.getIndexActivities())
                    .size(recallSize)
                    .knn(knn)
                    .source(s -> s.fetch(false))
                    .build();

            SearchResponse<JsonData> response = elasticsearchClient.search(request, JsonData.class);
            return mapHits(response, "semantic");
        }
        catch (IOException ex) {
            log.error("Elasticsearch kNN 检索失败", ex);
            String detail = ex.getMessage() != null ? ex.getMessage() : "";
            if (detail.contains("is not an inference service model")
                    || detail.contains("No known trained model")) {
                throw new BusinessException(
                        "语义向量模型未部署（model_id=" + modelId + "）。请在 database 目录执行: "
                                + ".\\init-es.ps1  （国内可用 $env:HF_ENDPOINT='https://hf-mirror.com'）后 "
                                + "POST /api/v1/search/index/rebuild");
            }
            throw new BusinessException("Elasticsearch 语义检索失败: " + detail);
        }
    }

    private List<ActivitySearchHit> executeQuerySearch(Query query, int recallSize, String channel) {
        try {
            SearchRequest request = new SearchRequest.Builder()
                    .index(elasticsearchProperties.getIndexActivities())
                    .size(recallSize)
                    .query(query)
                    .source(s -> s.fetch(false))
                    .build();

            SearchResponse<JsonData> response = elasticsearchClient.search(request, JsonData.class);
            return mapHits(response, channel);
        }
        catch (IOException ex) {
            log.error("Elasticsearch 检索失败 channel={}", channel, ex);
            throw new BusinessException("Elasticsearch 检索失败: " + ex.getMessage());
        }
    }

    private List<ActivitySearchHit> mapHits(SearchResponse<JsonData> response, String channel) {
        List<ActivitySearchHit> hits = new ArrayList<>();
        for (Hit<JsonData> hit : response.hits().hits()) {
            Long id = parseActivityId(hit);
            if (id == null) {
                continue;
            }
            double score = hit.score() != null ? hit.score() : 0.0;
            hits.add("keyword".equals(channel)
                    ? ActivitySearchHit.keyword(id, score)
                    : ActivitySearchHit.semantic(id, score));
        }
        return hits;
    }

    private List<Query> buildKnnFilters(ActivitySearchCriteria criteria) {
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(q -> q.bool(b -> b.mustNot(mn -> mn.term(t -> t.field("status").value("draft"))))));
        if (StringUtils.hasText(criteria.category())) {
            filters.add(Query.of(q -> q.term(t -> t.field("category").value(criteria.category()))));
        }
        if (StringUtils.hasText(criteria.status())) {
            filters.add(Query.of(q -> q.term(t -> t.field("status").value(criteria.status()))));
        }
        if (StringUtils.hasText(criteria.location())) {
            filters.add(Query.of(q -> q.term(t -> t.field("location").value(criteria.location()))));
        }
        return filters;
    }

    private Long parseActivityId(Hit<JsonData> hit) {
        if (hit.id() != null) {
            try {
                return Long.parseLong(hit.id());
            }
            catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BoolQuery buildFilterOnlyQuery(ActivitySearchCriteria criteria) {
        return BoolQuery.of(b -> {
            b.mustNot(mn -> mn.term(t -> t.field("status").value("draft")));
            applyFilters(b, criteria);
            return b;
        });
    }

    private BoolQuery buildBoolQuery(ActivitySearchCriteria criteria, Query mustQuery) {
        return BoolQuery.of(b -> {
            b.must(mustQuery);
            b.mustNot(mn -> mn.term(t -> t.field("status").value("draft")));
            applyFilters(b, criteria);
            return b;
        });
    }

    private void applyFilters(BoolQuery.Builder builder, ActivitySearchCriteria criteria) {
        if (StringUtils.hasText(criteria.category())) {
            builder.filter(f -> f.term(t -> t.field("category").value(criteria.category())));
        }
        if (StringUtils.hasText(criteria.status())) {
            builder.filter(f -> f.term(t -> t.field("status").value(criteria.status())));
        }
        if (StringUtils.hasText(criteria.location())) {
            builder.filter(f -> f.term(t -> t.field("location").value(criteria.location())));
        }
    }

    private Query buildKeywordQuery(String keyword) {
        return Query.of(q -> q.multiMatch(mm -> mm
                .query(keyword)
                .fields("title^3", "description^2", "search_text", "tags")));
    }
}
