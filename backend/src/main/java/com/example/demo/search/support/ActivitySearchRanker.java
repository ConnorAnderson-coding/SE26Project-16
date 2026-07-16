package com.example.demo.search.support;

import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.search.SearchSort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ActivitySearchRanker {

    private ActivitySearchRanker() {
    }

    public static List<ActivityResponse> sort(List<ActivityResponse> responses, SearchSort sort, double matchWeight) {
        if (responses.isEmpty()) {
            return responses;
        }

        List<ActivityResponse> sorted = new ArrayList<>(responses);
        switch (sort) {
            case HOT -> sorted.sort(Comparator.comparingDouble(ActivitySearchRanker::hotnessOf).reversed());
            case TIME -> sorted.sort(Comparator.comparing(ActivityResponse::getStartTime,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            case SIGNUP -> sorted.sort(Comparator.comparing(
                    ActivityResponse::getSignupCount,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            case COMPOSITE -> applyCompositeSort(sorted, matchWeight);
            case RELEVANCE -> sorted.sort(Comparator.comparing(
                    ActivityResponse::getSearchScore,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }
        return sorted;
    }

    private static void applyCompositeSort(List<ActivityResponse> responses, double matchWeight) {
        double w = clamp(matchWeight, 0.0, 1.0);
        double relevanceMin = responses.stream()
                .mapToDouble(r -> r.getSearchScore() != null ? r.getSearchScore() : 0.0)
                .min().orElse(0.0);
        double relevanceMax = responses.stream()
                .mapToDouble(r -> r.getSearchScore() != null ? r.getSearchScore() : 0.0)
                .max().orElse(0.0);
        double hotMin = responses.stream().mapToDouble(ActivitySearchRanker::hotnessOf).min().orElse(0.0);
        double hotMax = responses.stream().mapToDouble(ActivitySearchRanker::hotnessOf).max().orElse(0.0);

        for (ActivityResponse response : responses) {
            double relevanceNorm = normalize(
                    response.getSearchScore() != null ? response.getSearchScore() : 0.0,
                    relevanceMin,
                    relevanceMax);
            double hotNorm = normalize(hotnessOf(response), hotMin, hotMax);
            double composite = w * relevanceNorm + (1.0 - w) * hotNorm;
            response.setCompositeScore(composite);
        }

        responses.sort(Comparator.comparing(
                ActivityResponse::getCompositeScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
    }

    static double hotnessOf(ActivityResponse response) {
        return response.getHotnessScore() != null ? response.getHotnessScore() : 0.0;
    }

    private static double normalize(double value, double min, double max) {
        if (max <= min) {
            return 1.0;
        }
        return (value - min) / (max - min);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
