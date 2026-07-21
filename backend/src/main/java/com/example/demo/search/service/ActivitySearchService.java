package com.example.demo.search.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.BusinessException;
import com.example.demo.common.PageResult;
import com.example.demo.config.ElasticsearchProperties;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.search.ActivitySearchCriteria;
import com.example.demo.search.ActivitySearchHit;
import com.example.demo.search.repository.ActivitySearchRepository;
import com.example.demo.search.support.ActivitySearchRanker;
import com.example.demo.search.support.HybridRelevanceScorer;
import com.example.demo.search.support.SemanticScoreFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ActivitySearchService {

    private final ActivitySearchRepository activitySearchRepository;
    private final ActivityRepository activityRepository;
    private final ElasticsearchProperties elasticsearchProperties;

    /** True when the activities index has no documents (needs rebuild). */
    public boolean isIndexEmpty() {
        return activitySearchRepository.countAll() <= 0;
    }

    @Transactional(readOnly = true)
    public PageResult<ActivityResponse> search(ActivitySearchCriteria criteria) {
        if (!criteria.hasKeyword()) {
            throw new BusinessException("语义检索需要提供 keyword 参数");
        }

        int recallSize = elasticsearchProperties.getSearchRecallSize();
        List<ActivitySearchHit> rankedHits = switch (criteria.mode()) {
            case KEYWORD -> activitySearchRepository.searchKeyword(criteria, recallSize);
            case SEMANTIC -> SemanticScoreFilter.filterSemanticOnly(
                    List.of(),
                    searchSemanticWithFallback(criteria, recallSize),
                    elasticsearchProperties.getSemanticAbsoluteThreshold());
            case HYBRID -> searchHybrid(criteria, recallSize);
        };

        List<ActivityResponse> hydrated = hydrateActivities(rankedHits);
        List<ActivityResponse> sorted = ActivitySearchRanker.sort(
                hydrated, criteria.sort(), criteria.matchWeight());

        long total = sorted.size();
        int fromIndex = criteria.page() * criteria.size();
        if (fromIndex >= sorted.size()) {
            return new PageResult<>(List.of(), criteria.page(), criteria.size(), total, pageCount(total, criteria.size()));
        }

        int toIndex = Math.min(fromIndex + criteria.size(), sorted.size());
        List<ActivityResponse> pageContent = sorted.subList(fromIndex, toIndex);

        return new PageResult<>(
                pageContent,
                criteria.page(),
                criteria.size(),
                total,
                pageCount(total, criteria.size()));
    }

    private List<ActivitySearchHit> searchSemanticWithFallback(ActivitySearchCriteria criteria, int recallSize) {
        try {
            List<ActivitySearchHit> semanticHits = activitySearchRepository.searchSemantic(criteria, recallSize);
            if (!semanticHits.isEmpty()) {
                return semanticHits;
            }
        }
        catch (BusinessException ex) {
            log.warn("稠密向量语义检索不可用，降级为 BM25: {}", ex.getMessage());
        }
        return activitySearchRepository.searchKeyword(criteria, recallSize);
    }

    private List<ActivitySearchHit> searchHybrid(ActivitySearchCriteria criteria, int recallSize) {
        List<ActivitySearchHit> keywordHits = activitySearchRepository.searchKeyword(criteria, recallSize);
        List<ActivitySearchHit> semanticHits;
        try {
            semanticHits = activitySearchRepository.searchSemantic(criteria, recallSize);
        }
        catch (BusinessException ex) {
            log.warn("混合检索语义通路失败，仅使用 BM25: {}", ex.getMessage());
            return keywordHits;
        }

        List<ActivitySearchHit> filteredSemantic = SemanticScoreFilter.filterSemanticOnly(
                keywordHits,
                semanticHits,
                elasticsearchProperties.getSemanticAbsoluteThreshold());

        return HybridRelevanceScorer.score(
                keywordHits,
                filteredSemantic,
                elasticsearchProperties.getRelevanceBm25Weight(),
                elasticsearchProperties.getRelevanceSemanticWeight(),
                elasticsearchProperties.getRelevanceKeywordBonus(),
                elasticsearchProperties.getRelevanceSemanticOnlyWeight());
    }

    private List<ActivityResponse> hydrateActivities(List<ActivitySearchHit> hits) {
        List<Long> ids = hits.stream().map(ActivitySearchHit::activityId).toList();
        Map<Long, Activity> activityMap = activityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<ActivityResponse> responses = new ArrayList<>();
        for (ActivitySearchHit hit : hits) {
            Activity activity = activityMap.get(hit.activityId());
            if (activity == null) {
                continue;
            }
            ActivityResponse response = DtoMapper.toActivityResponse(activity);
            response.setSearchScore(hit.fusedScore());
            response.setSearchChannel(hit.channel());
            response.setKeywordScore(hit.keywordScore());
            response.setSemanticScore(hit.semanticScore());
            responses.add(response);
        }
        return responses;
    }

    private int pageCount(long total, int size) {
        if (total == 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }
}
