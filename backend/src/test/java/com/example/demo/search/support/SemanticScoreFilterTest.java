package com.example.demo.search.support;

import com.example.demo.search.ActivitySearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticScoreFilterTest {

    @Test
    void shouldKeepKeywordHitsAndScoresAboveAbsoluteThreshold() {
        List<ActivitySearchHit> keyword = List.of(ActivitySearchHit.keyword(2L, 5.0));
        List<ActivitySearchHit> semantic = List.of(
                ActivitySearchHit.semantic(2L, 0.40),
                ActivitySearchHit.semantic(6L, 0.72),
                ActivitySearchHit.semantic(1L, 0.50));

        List<ActivitySearchHit> filtered = SemanticScoreFilter.filterSemanticOnly(keyword, semantic, 0.55);

        assertEquals(2, filtered.size());
        assertEquals(2L, filtered.get(0).activityId());
        assertEquals(6L, filtered.get(1).activityId());
    }

    @Test
    void shouldDropAllSemanticOnlyHitsBelowThreshold() {
        List<ActivitySearchHit> keyword = List.of();
        List<ActivitySearchHit> semantic = List.of(
                ActivitySearchHit.semantic(1L, 0.40),
                ActivitySearchHit.semantic(2L, 0.54));

        List<ActivitySearchHit> filtered = SemanticScoreFilter.filterSemanticOnly(keyword, semantic, 0.55);

        assertTrue(filtered.isEmpty());
    }

    @Test
    void defaultProductionThresholdKeepsStrongSemanticAndExemptsKeyword() {
        List<ActivitySearchHit> keyword = List.of(ActivitySearchHit.keyword(2L, 5.0));
        List<ActivitySearchHit> semantic = List.of(
                ActivitySearchHit.semantic(2L, 0.88),
                ActivitySearchHit.semantic(7L, 0.91),
                ActivitySearchHit.semantic(1L, 0.89));

        List<ActivitySearchHit> filtered = SemanticScoreFilter.filterSemanticOnly(keyword, semantic, 0.90);

        assertEquals(2, filtered.size());
        assertTrue(filtered.stream().anyMatch(h -> h.activityId().equals(2L)));
        assertTrue(filtered.stream().anyMatch(h -> h.activityId().equals(7L)));
    }
}
