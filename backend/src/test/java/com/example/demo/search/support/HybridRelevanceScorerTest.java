package com.example.demo.search.support;

import com.example.demo.search.ActivitySearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRelevanceScorerTest {

    private static final double W_BM25 = 0.50;
    private static final double W_SEM = 0.35;
    private static final double BONUS = 0.15;
    private static final double W_SEM_ONLY = 0.90;

    @Test
    void keywordHitsRankAboveSemanticOnlyWithSimilarScores() {
        List<ActivitySearchHit> keyword = List.of(ActivitySearchHit.keyword(2L, 4.0));
        List<ActivitySearchHit> semantic = List.of(
                ActivitySearchHit.semantic(2L, 0.93),
                ActivitySearchHit.semantic(7L, 0.93));

        List<ActivitySearchHit> ranked = HybridRelevanceScorer.score(
                keyword, semantic, W_BM25, W_SEM, BONUS, W_SEM_ONLY);

        assertEquals(2, ranked.size());
        assertEquals(2L, ranked.get(0).activityId());
        assertEquals(7L, ranked.get(1).activityId());
        assertTrue(ranked.get(0).fusedScore() > ranked.get(1).fusedScore());
    }

    @Test
    void higherBm25WinsAmongKeywordHitsWhenSemanticEqual() {
        List<ActivitySearchHit> keyword = List.of(
                ActivitySearchHit.keyword(1L, 2.0),
                ActivitySearchHit.keyword(2L, 4.0));
        List<ActivitySearchHit> semantic = List.of(
                ActivitySearchHit.semantic(1L, 0.92),
                ActivitySearchHit.semantic(2L, 0.92));

        List<ActivitySearchHit> ranked = HybridRelevanceScorer.score(
                keyword, semantic, W_BM25, W_SEM, BONUS, W_SEM_ONLY);

        assertEquals(2L, ranked.get(0).activityId());
        assertEquals(1L, ranked.get(1).activityId());
    }

    @Test
    void semanticOnlyUsesScaledSemanticScore() {
        List<ActivitySearchHit> ranked = HybridRelevanceScorer.score(
                List.of(),
                List.of(ActivitySearchHit.semantic(9L, 0.94)),
                W_BM25, W_SEM, BONUS, W_SEM_ONLY);

        assertEquals(1, ranked.size());
        assertEquals(0.90 * 0.94, ranked.get(0).fusedScore(), 1e-9);
        assertEquals(9L, ranked.get(0).activityId());
    }
}
