package com.example.demo.search;

public record ActivitySearchHit(
        Long activityId,
        Double keywordScore,
        Double semanticScore,
        double fusedScore,
        String channel) {

    public static ActivitySearchHit keyword(Long activityId, double score) {
        return new ActivitySearchHit(activityId, score, null, score, "keyword");
    }

    public static ActivitySearchHit semantic(Long activityId, double score) {
        return new ActivitySearchHit(activityId, null, score, score, "semantic");
    }

    public static ActivitySearchHit hybrid(Long activityId, Double keywordScore, Double semanticScore, double fusedScore) {
        return new ActivitySearchHit(activityId, keywordScore, semanticScore, fusedScore, "hybrid");
    }
}
