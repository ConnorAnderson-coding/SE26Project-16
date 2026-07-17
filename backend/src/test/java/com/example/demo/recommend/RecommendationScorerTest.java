package com.example.demo.recommend;

import com.example.demo.config.ElasticsearchProperties;
import com.example.demo.entity.Activity;
import com.example.demo.recommend.support.ActivityTimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationScorerTest {

    private RecommendationScorer scorer;
    private ElasticsearchProperties props;

    @BeforeEach
    void setUp() {
        props = new ElasticsearchProperties();
        props.setRecommendWeightSim(0.55);
        props.setRecommendWeightTag(0.15);
        props.setRecommendWeightSocial(0.05);
        props.setRecommendWeightHot(0.10);
        props.setRecommendWeightTime(0.05);
        props.setRecommendAuxScale(0.25);
        props.setRecommendSimSpanMin(0.03);
        props.setRecommendTagCap(2.0);
        props.setRecommendSocialRef(3.5);
        scorer = new RecommendationScorer(props);
    }

    @Test
    void hardFilterRemovesSignedUpAndConflicts() {
        Activity signed = activity(1L, "sports",
                LocalDateTime.of(2026, 7, 20, 10, 0),
                LocalDateTime.of(2026, 7, 20, 12, 0));
        Activity overlap = activity(2L, "club",
                LocalDateTime.of(2026, 7, 20, 11, 0),
                LocalDateTime.of(2026, 7, 20, 13, 0));
        Activity ok = activity(3L, "arts",
                LocalDateTime.of(2026, 7, 21, 10, 0),
                LocalDateTime.of(2026, 7, 21, 12, 0));

        List<Activity> kept = scorer.hardFilter(
                List.of(signed, overlap, ok),
                Set.of(1L),
                List.of(new RecommendationScorer.SignedActivityWindow(
                        1L, signed.getStartTime(), signed.getEndTime())));

        assertEquals(1, kept.size());
        assertEquals(3L, kept.get(0).getId());
    }

    @Test
    void scorePrefersHigherSimWhenOtherFactorsEqual() {
        Activity a = activity(1L, "sports",
                LocalDateTime.of(2026, 7, 20, 20, 0),
                LocalDateTime.of(2026, 7, 20, 22, 0));
        a.setTags(List.of("羽毛球"));
        a.setSignupCount(10);
        a.setFavoriteCount(1);
        a.setOrganizerId("org1");

        Activity b = activity(2L, "sports",
                LocalDateTime.of(2026, 7, 21, 20, 0),
                LocalDateTime.of(2026, 7, 21, 22, 0));
        b.setTags(List.of("羽毛球"));
        b.setSignupCount(10);
        b.setFavoriteCount(1);
        b.setOrganizerId("org1");

        List<RecommendationScorer.ScoredActivity> ranked = scorer.scoreAndRank(
                List.of(a, b),
                Map.of(1L, 0.95, 2L, 0.80),
                List.of("羽毛球"),
                List.of("weekday_evening"),
                Map.of("org1", 1.0),
                false);

        assertEquals(1L, ranked.get(0).activity().getId());
        assertTrue(ranked.get(0).finalScore() > ranked.get(1).finalScore());
    }

    @Test
    void narrowSimSpanKeepsAbsoluteScoresNotMinMaxInflation() {
        double[] component = scorer.resolveSimComponent(new double[] {0.929, 0.921, 0.911}, false);
        assertEquals(0.929, component[0], 1e-9);
        assertEquals(0.921, component[1], 1e-9);
        assertEquals(0.911, component[2], 1e-9);
    }

    @Test
    void socialCannotDominateWhenAbsoluteSimEqual() {
        // Identical sim + zero tags: max social contrib ≈ ε * (wSoc / wAux) ≈ 0.036
        Activity highSocial = activity(7L, "sports",
                LocalDateTime.of(2026, 7, 21, 20, 0),
                LocalDateTime.of(2026, 7, 21, 22, 0));
        highSocial.setOrganizerId("lisi");
        highSocial.setSignupCount(10);
        highSocial.setFavoriteCount(0);

        Activity lowSocial = activity(10L, "academic",
                LocalDateTime.of(2026, 7, 21, 20, 0),
                LocalDateTime.of(2026, 7, 21, 22, 0));
        lowSocial.setOrganizerId("other");
        lowSocial.setSignupCount(10);
        lowSocial.setFavoriteCount(0);

        List<RecommendationScorer.ScoredActivity> ranked = scorer.scoreAndRank(
                List.of(highSocial, lowSocial),
                Map.of(7L, 0.92, 10L, 0.92),
                List.of("AI", "摄影", "羽毛球"),
                List.of("weekday_evening"),
                Map.of("lisi", 3.5, "other", 0.0),
                false);

        assertEquals(7L, ranked.get(0).activity().getId());
        double gap = ranked.get(0).finalScore() - ranked.get(1).finalScore();
        assertTrue(gap < 0.05, "social aux must stay small, gap=" + gap);
    }

    @Test
    void tagHitBeatsEqualSimZeroTag() {
        Activity tagged = activity(1L, "sports",
                LocalDateTime.of(2026, 7, 21, 20, 0),
                LocalDateTime.of(2026, 7, 21, 22, 0));
        tagged.setTags(List.of("羽毛球"));
        tagged.setOrganizerId("org");

        Activity plain = activity(2L, "club",
                LocalDateTime.of(2026, 7, 21, 20, 0),
                LocalDateTime.of(2026, 7, 21, 22, 0));
        plain.setTags(List.of("桌游"));
        plain.setOrganizerId("org");

        List<RecommendationScorer.ScoredActivity> ranked = scorer.scoreAndRank(
                List.of(tagged, plain),
                Map.of(1L, 0.90, 2L, 0.90),
                List.of("羽毛球"),
                List.of("weekday_evening"),
                Map.of("org", 0.0),
                false);

        assertEquals(1L, ranked.get(0).activity().getId());
    }

    @Test
    void weekendSlotMapping() {
        assertEquals(ActivityTimeSlot.WEEKEND,
                ActivityTimeSlot.fromStartTime(LocalDateTime.of(2026, 7, 18, 15, 0)));
        assertEquals(1.0, ActivityTimeSlot.timeFit(
                LocalDateTime.of(2026, 7, 18, 15, 0), List.of("weekend")));
        assertEquals(0.0, ActivityTimeSlot.timeFit(
                LocalDateTime.of(2026, 7, 18, 15, 0), List.of("weekday_morning")));
    }

    @Test
    void socialFormulaIncreasesWithCounts() {
        double low = SocialAffinityService.formula(0, 0, 0);
        double high = SocialAffinityService.formula(3, 2, 1);
        assertTrue(high > low);
    }

    @Test
    void reasonsHighlightSocialWhenContentBelowMedian() {
        List<String> reasons = RecommendationScorer.buildReasons(
                false,
                0.91,
                0.93,
                0.91,
                0.0,
                0.8,
                0.2,
                0.0,
                true,
                0.0,
                0.04,
                0.01,
                0.0,
                0.91);
        assertTrue(reasons.contains(RecommendationScorer.REASON_SOCIAL));
    }

    @Test
    void scoreAndRankAttachesReasons() {
        Activity a = activity(1L, "sports",
                LocalDateTime.of(2026, 7, 20, 20, 0),
                LocalDateTime.of(2026, 7, 20, 22, 0));
        a.setTags(List.of("羽毛球"));
        a.setOrganizerId("org1");
        a.setSignupCount(80);
        a.setFavoriteCount(20);

        List<RecommendationScorer.ScoredActivity> ranked = scorer.scoreAndRank(
                List.of(a),
                Map.of(1L, 0.94),
                List.of("羽毛球"),
                List.of("weekday_evening"),
                Map.of("org1", 2.0),
                false);

        assertTrue(ranked.get(0).reasons().contains(RecommendationScorer.REASON_INTEREST));
    }

    private static Activity activity(long id, String category, LocalDateTime start, LocalDateTime end) {
        Activity a = new Activity();
        a.setId(id);
        a.setCategory(category);
        a.setStartTime(start);
        a.setEndTime(end);
        a.setStatus("published");
        a.setSignupCount(0);
        a.setFavoriteCount(0);
        return a;
    }
}
