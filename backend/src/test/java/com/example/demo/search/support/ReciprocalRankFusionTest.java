package com.example.demo.search.support;

import com.example.demo.search.ActivitySearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReciprocalRankFusionTest {

    @Test
    void fuseShouldMergeAndRankByRrfScore() {
        List<ActivitySearchHit> keyword = List.of(
                ActivitySearchHit.keyword(1L, 10.0),
                ActivitySearchHit.keyword(2L, 8.0),
                ActivitySearchHit.keyword(3L, 6.0));
        List<ActivitySearchHit> semantic = List.of(
                ActivitySearchHit.semantic(2L, 9.0),
                ActivitySearchHit.semantic(4L, 7.0),
                ActivitySearchHit.semantic(1L, 5.0));

        List<ActivitySearchHit> fused = ReciprocalRankFusion.fuse(List.of(keyword, semantic), 60);

        assertEquals(4, fused.size());
        assertEquals(2L, fused.get(0).activityId());
        assertEquals(1L, fused.get(1).activityId());
        assertEquals(10.0, fused.get(1).keywordScore());
        assertEquals(8.0, fused.get(0).keywordScore());
        assertEquals(9.0, fused.get(0).semanticScore());
        assertEquals("hybrid", fused.get(0).channel());
    }
}
