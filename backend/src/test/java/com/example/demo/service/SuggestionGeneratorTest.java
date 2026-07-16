package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.dto.analytics.SuggestionItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionGeneratorTest {

    @Test
    void fallbackSuggestionsCoverRequiredAnalysisDimensions() {
        SuggestionGenerator generator = new SuggestionGenerator(null, null);
        ActivityMetrics metrics = ActivityMetrics.builder()
                .activityId(17L)
                .signupRate(BigDecimal.valueOf(35.0))
                .attendanceRate(BigDecimal.valueOf(57.1))
                .avgRating(BigDecimal.valueOf(2.80))
                .feedbackContents(List.of("Projector, seats and route need improvement."))
                .build();

        var suggestions = generator.fallbackSafe(metrics);
        var categories = suggestions.stream()
                .map(SuggestionItem::getCategory)
                .toList();

        assertEquals(4, suggestions.size());
        assertTrue(categories.contains("promotion"));
        assertTrue(categories.contains("schedule"));
        assertTrue(categories.contains("venue"));
        assertTrue(categories.contains("content"));
    }
}
