package com.example.demo.analytics.service;

import com.example.demo.analytics.dto.ActivityMetrics;
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
                .feedbackContents(List.of("2分：后排看不清投影，插座也不够。"))
                .build();

        var suggestions = generator.fallbackSafe(metrics);
        var categories = suggestions.stream()
                .map(item -> item.getCategory())
                .toList();

        assertEquals(4, suggestions.size());
        assertTrue(categories.contains("promotion"));
        assertTrue(categories.contains("schedule"));
        assertTrue(categories.contains("venue"));
        assertTrue(categories.contains("content"));
    }
}
