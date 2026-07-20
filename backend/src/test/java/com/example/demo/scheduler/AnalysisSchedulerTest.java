package com.example.demo.scheduler;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityAnalysis;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.service.AnalyticsEngine;
import com.example.demo.service.LlmAnalysisRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisSchedulerTest {

    @Mock ActivityRepository activityRepository;
    @Mock ActivityAnalysisRepository analysisRepository;
    @Mock AnalyticsEngine analyticsEngine;
    @Mock LlmAnalysisRunner llmAnalysisRunner;
    @InjectMocks AnalysisScheduler scheduler;

    @Test
    void dispatchesAnalysisForYesterdayEndedActivities() {
        Activity a = new Activity();
        a.setId(10L);
        a.setTitle("昨日活动");
        when(activityRepository.findEndedBetween(any(), any())).thenReturn(List.of(a));
        when(analysisRepository.findByActivityId(10L)).thenReturn(Optional.empty());
        when(analysisRepository.findBySuggestionSource("rule")).thenReturn(List.of());
        ActivityMetrics metrics = ActivityMetrics.builder().activityId(10L).build();
        when(analyticsEngine.computeMetrics(10L)).thenReturn(metrics);

        scheduler.refreshEndedActivitiesAnalysis();

        verify(llmAnalysisRunner).runAsync(eq(10L), eq(metrics));
    }

    @Test
    void skipsWhenExistingAnalysisIsFresh() {
        Activity a = new Activity();
        a.setId(11L);
        ActivityAnalysis existing = new ActivityAnalysis();
        existing.setGeneratedAt(LocalDateTime.now());
        when(activityRepository.findEndedBetween(any(), any())).thenReturn(List.of(a));
        when(analysisRepository.findByActivityId(11L)).thenReturn(Optional.of(existing));
        // 数据新鲜度：全部早于 generatedAt → 不 stale
        Object[] freshness = new Object[]{
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(2)
        };
        when(activityRepository.findDataFreshness(11L)).thenReturn(List.<Object[]>of(freshness));
        when(analysisRepository.findBySuggestionSource("rule")).thenReturn(List.of());

        scheduler.refreshEndedActivitiesAnalysis();

        verify(llmAnalysisRunner, never()).runAsync(anyLong(), any());
        verify(analyticsEngine, never()).computeMetrics(anyLong());
    }

    @Test
    void redispatchesWhenDataIsStale() {
        Activity a = new Activity();
        a.setId(12L);
        ActivityAnalysis existing = new ActivityAnalysis();
        existing.setGeneratedAt(LocalDateTime.now().minusDays(3));
        when(activityRepository.findEndedBetween(any(), any())).thenReturn(List.of(a));
        when(analysisRepository.findByActivityId(12L)).thenReturn(Optional.of(existing));
        Object[] staleFreshness = new Object[]{
                LocalDateTime.now().minusHours(1), // 新数据
                null, null, null
        };
        when(activityRepository.findDataFreshness(12L)).thenReturn(List.<Object[]>of(staleFreshness));
        when(analysisRepository.findBySuggestionSource("rule")).thenReturn(List.of());
        ActivityMetrics metrics = ActivityMetrics.builder().activityId(12L).build();
        when(analyticsEngine.computeMetrics(12L)).thenReturn(metrics);

        scheduler.refreshEndedActivitiesAnalysis();

        verify(llmAnalysisRunner).runAsync(12L, metrics);
    }

    @Test
    void upgradesRuleBasedRecordsWithoutDuplicatingYesterdayBatch() {
        Activity yesterday = new Activity();
        yesterday.setId(20L);
        when(activityRepository.findEndedBetween(any(), any())).thenReturn(List.of(yesterday));
        when(analysisRepository.findByActivityId(20L)).thenReturn(Optional.empty());
        when(analyticsEngine.computeMetrics(20L))
                .thenReturn(ActivityMetrics.builder().activityId(20L).build());

        ActivityAnalysis ruleRow = new ActivityAnalysis();
        ruleRow.setId(99L);
        ruleRow.setActivityId(20L); // 与昨日批同一活动 → 不应二次派发

        ActivityAnalysis otherRule = new ActivityAnalysis();
        otherRule.setId(100L);
        otherRule.setActivityId(21L);

        when(analysisRepository.findBySuggestionSource("rule"))
                .thenReturn(List.of(ruleRow, otherRule));
        when(analyticsEngine.computeMetrics(21L))
                .thenReturn(ActivityMetrics.builder().activityId(21L).build());

        scheduler.refreshEndedActivitiesAnalysis();

        ArgumentCaptor<Long> ids = ArgumentCaptor.forClass(Long.class);
        verify(llmAnalysisRunner, times(2)).runAsync(ids.capture(), any());
        assertThat(ids.getAllValues()).containsExactlyInAnyOrder(20L, 21L);
    }

    @Test
    void isDataStaleWhenGeneratedAtNull() {
        ActivityAnalysis a = new ActivityAnalysis();
        a.setGeneratedAt(null);
        assertThat(scheduler.isDataStale(1L, a)).isTrue();
    }
}
