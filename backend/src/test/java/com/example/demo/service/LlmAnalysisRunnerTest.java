package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.dto.analytics.SuggestionItem;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityAnalysis;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.ActivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 直接调用 runAsync（不经过 Spring 代理），验证 LLM 成功 / 失败降级 / 幂等跳过。
 */
@ExtendWith(MockitoExtension.class)
class LlmAnalysisRunnerTest {

    @Mock SuggestionGenerator suggestionGenerator;
    @Mock LlmClient llmClient;
    @Mock ActivityAnalysisRepository analysisRepository;
    @Mock ActivityRepository activityRepository;
    @InjectMocks LlmAnalysisRunner runner;

    private ActivityMetrics metrics() {
        return ActivityMetrics.builder()
                .activityId(3L)
                .activityTitle("摄影社户外采风")
                .signupRate(java.math.BigDecimal.TEN)
                .attendanceRate(java.math.BigDecimal.TEN)
                .build();
    }

    private Activity activity() {
        Activity a = new Activity();
        a.setId(3L);
        a.setTitle("摄影社户外采风");
        return a;
    }

    @Test
    void llmSuccessPersistsLlmSource() {
        when(analysisRepository.findByActivityId(3L)).thenReturn(Optional.empty());
        when(activityRepository.findById(3L)).thenReturn(Optional.of(activity()));
        when(llmClient.getModel()).thenReturn("deepseek-chat");
        when(suggestionGenerator.generateSuggestions(any())).thenReturn(List.of(
                SuggestionItem.builder().id("1").category("promotion").priority("high").content("宣传").build(),
                SuggestionItem.builder().id("2").category("schedule").priority("medium").content("时间").build(),
                SuggestionItem.builder().id("3").category("venue").priority("low").content("场地").build(),
                SuggestionItem.builder().id("4").category("content").priority("medium").content("内容").build()
        ));

        runner.runAsync(3L, metrics());

        ArgumentCaptor<ActivityAnalysis> captor = ArgumentCaptor.forClass(ActivityAnalysis.class);
        // pending + final
        verify(analysisRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        ActivityAnalysis last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(last.getSuggestionSource()).isEqualTo("llm");
        assertThat(last.getAnalysisStatus()).isEqualTo("ready");
        assertThat(last.getSuggestionModel()).isEqualTo("deepseek-chat");
        assertThat(last.getSuggestions()).isNotEmpty();
        assertThat(last.getFailureReason()).isNull();
    }

    @Test
    void llmFailureFallsBackToRuleTemplate() {
        when(analysisRepository.findByActivityId(3L)).thenReturn(Optional.empty());
        when(activityRepository.findById(3L)).thenReturn(Optional.of(activity()));
        when(llmClient.getModel()).thenReturn("deepseek-chat");
        when(suggestionGenerator.generateSuggestions(any()))
                .thenThrow(new LlmClient.LlmCallException("timeout", true));
        when(suggestionGenerator.fallbackSafe(any())).thenReturn(List.of(
                SuggestionItem.builder().id("1").category("promotion").priority("high").content("规则宣传").build(),
                SuggestionItem.builder().id("2").category("schedule").priority("medium").content("规则时间").build(),
                SuggestionItem.builder().id("3").category("venue").priority("low").content("规则场地").build(),
                SuggestionItem.builder().id("4").category("content").priority("medium").content("规则内容").build()
        ));

        runner.runAsync(3L, metrics());

        ArgumentCaptor<ActivityAnalysis> captor = ArgumentCaptor.forClass(ActivityAnalysis.class);
        verify(analysisRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        ActivityAnalysis last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(last.getSuggestionSource()).isEqualTo("rule");
        assertThat(last.getAnalysisStatus()).isEqualTo("ready");
        assertThat(last.getFailureReason()).contains("timeout");
        assertThat(last.getSuggestions()).isNotEmpty();
    }

    @Test
    void skipsWhenLlmReadyAlreadyExists() {
        ActivityAnalysis existing = new ActivityAnalysis();
        existing.setSuggestionSource("llm");
        existing.setAnalysisStatus("ready");
        when(analysisRepository.findByActivityId(3L)).thenReturn(Optional.of(existing));

        runner.runAsync(3L, metrics());

        verify(suggestionGenerator, never()).generateSuggestions(any());
        verify(analysisRepository, never()).save(any());
    }

    @Test
    void upgradeKeepsRuleWhenLlmFails() {
        ActivityAnalysis existing = new ActivityAnalysis();
        existing.setSuggestionSource("rule");
        existing.setAnalysisStatus("ready");
        existing.setSuggestions(List.of(java.util.Map.of("id", "old", "content", "旧规则")));
        when(analysisRepository.findByActivityId(3L)).thenReturn(Optional.of(existing));
        when(suggestionGenerator.generateSuggestions(any()))
                .thenThrow(new LlmClient.LlmCallException("401", false));

        runner.runAsync(3L, metrics());

        // 升级失败：不覆盖原记录
        verify(analysisRepository, never()).save(any());
        verify(suggestionGenerator, never()).fallbackSafe(any());
    }
}
