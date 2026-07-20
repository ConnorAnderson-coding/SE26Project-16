package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.dto.analytics.SuggestionItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionGeneratorTest {

    @Mock LlmClient llmClient;
    @Mock SummaryBuilder summaryBuilder;
    @InjectMocks SuggestionGenerator suggestionGenerator;

    @Test
    void generateSuggestionsMapsLlmJsonToEntities() {
        when(summaryBuilder.build(org.mockito.ArgumentMatchers.any())).thenReturn("SUMMARY");
        when(llmClient.generateImprovements("SUMMARY")).thenReturn(List.of(
                Map.of("id", "id-1", "category", "promotion", "priority", "high", "content", "加强宣传"),
                Map.of("id", "id-2", "category", "schedule", "priority", "medium", "content", "优化时间"),
                Map.of("id", "id-3", "category", "venue", "priority", "low", "content", "改善场地"),
                Map.of("id", "id-4", "category", "content", "priority", "medium", "content", "丰富内容")
        ));

        List<SuggestionItem> items = suggestionGenerator.generateSuggestions(
                ActivityMetrics.builder().activityId(1L).build());

        assertThat(items).hasSize(4);
        assertThat(items.get(0).getId()).isEqualTo("id-1");
        assertThat(items.get(0).getCategory()).isEqualTo("promotion");
        assertThat(items.get(0).getPriority()).isEqualTo("high");
        assertThat(items.get(0).getContent()).isEqualTo("加强宣传");
        assertThat(items).extracting(SuggestionItem::getCategory)
                .containsExactly("promotion", "schedule", "venue", "content");
    }

    @Test
    void generateSuggestionsPropagatesLlmFailure() {
        when(summaryBuilder.build(org.mockito.ArgumentMatchers.any())).thenReturn("SUMMARY");
        when(llmClient.generateImprovements(anyString()))
                .thenThrow(new LlmClient.LlmCallException("timeout", true));

        assertThatThrownBy(() -> suggestionGenerator.generateSuggestions(
                ActivityMetrics.builder().activityId(1L).build()))
                .isInstanceOf(LlmClient.LlmCallException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void fallbackSafeCoversFourCategoriesAndPriorities() {
        ActivityMetrics metrics = ActivityMetrics.builder()
                .signupRate(new BigDecimal("20.0"))
                .attendanceRate(new BigDecimal("40.0"))
                .avgRating(new BigDecimal("3.5"))
                .feedbackContents(List.of("教室太远音响很差"))
                .build();

        List<SuggestionItem> items = suggestionGenerator.fallbackSafe(metrics);

        assertThat(items).hasSize(4);
        assertThat(items).extracting(SuggestionItem::getCategory)
                .containsExactly("promotion", "schedule", "venue", "content");
        assertThat(items).extracting(SuggestionItem::getPriority)
                .contains("high"); // 低转化/低到场/低评分/场地关键词 → high
        assertThat(items).allMatch(i -> i.getId() != null && !i.getId().isBlank());
        assertThat(items).allMatch(i -> i.getContent() != null && i.getContent().length() > 10);
    }

    @Test
    void fallbackSafeUsesMediumWhenMetricsHealthy() {
        ActivityMetrics metrics = ActivityMetrics.builder()
                .signupRate(new BigDecimal("80.0"))
                .attendanceRate(new BigDecimal("90.0"))
                .avgRating(new BigDecimal("4.8"))
                .feedbackContents(List.of("非常棒"))
                .build();

        List<SuggestionItem> items = suggestionGenerator.fallbackSafe(metrics);

        assertThat(items).extracting(SuggestionItem::getPriority)
                .containsOnly("medium");
    }

    @Test
    void fallbackSafeWorksWithNullMetricsFields() {
        List<SuggestionItem> items = suggestionGenerator.fallbackSafe(
                ActivityMetrics.builder().build());

        assertThat(items).hasSize(4);
        assertThat(items).extracting(SuggestionItem::getCategory)
                .contains("promotion", "schedule", "venue", "content");
    }
}
