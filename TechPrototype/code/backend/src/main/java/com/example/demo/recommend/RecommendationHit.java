package com.example.demo.recommend;

/**
 * @param activityId activity id
 * @param simScore   ES kNN score ≈ (1+cos)/2, or 0 for cold-start candidates
 */
public record RecommendationHit(long activityId, double simScore) {
}
