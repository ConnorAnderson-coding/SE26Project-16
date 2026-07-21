package com.example.demo.search.support;

import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.search.SearchSort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivitySearchRankerTest {

    @Test
    void compositeSortShouldWeightRelevanceAndHot() {
        ActivityResponse highRelevance = response(1L, 0.9, 0.5, LocalDateTime.of(2026, 7, 20, 10, 0));
        ActivityResponse highHot = response(2L, 0.1, 8.0, LocalDateTime.of(2026, 7, 21, 10, 0));

        List<ActivityResponse> sorted = ActivitySearchRanker.sort(
                List.of(highHot, highRelevance), SearchSort.COMPOSITE, 0.7);

        assertEquals(1L, sorted.get(0).getId());
        assertEquals(2L, sorted.get(1).getId());
    }

    @Test
    void hotSortShouldOrderByHotnessScore() {
        ActivityResponse a = response(1L, 0.5, 1.0, LocalDateTime.now());
        ActivityResponse b = response(2L, 0.5, 5.0, LocalDateTime.now());

        List<ActivityResponse> sorted = ActivitySearchRanker.sort(List.of(a, b), SearchSort.HOT, 0.7);

        assertEquals(2L, sorted.get(0).getId());
    }

    private ActivityResponse response(Long id, double relevance, double hotness, LocalDateTime startTime) {
        return ActivityResponse.builder()
                .id(id)
                .searchScore(relevance)
                .hotnessScore(hotness)
                .startTime(startTime)
                .build();
    }
}
