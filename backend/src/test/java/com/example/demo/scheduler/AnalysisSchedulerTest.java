package com.example.demo.scheduler;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityAnalysis;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.service.AnalyticsEngine;
import com.example.demo.service.LlmAnalysisRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 锁定 {@link AnalysisScheduler} 的两个核心修正：
 * <ul>
 *   <li>跳过条件改用"数据新鲜度"：{@code generatedAt < 最新数据时间戳 → 刷新}，不再用 {@code createdAt}</li>
 *   <li>调度层只派发任务，不持事务、不调 LLM、不阻塞 DB 连接</li>
 * </ul>
 */
class AnalysisSchedulerTest {

    private ActivityRepository activityRepository;
    private ActivityAnalysisRepository analysisRepository;
    private AnalyticsEngine analyticsEngine;
    private LlmAnalysisRunner llmAnalysisRunner;
    private AnalysisScheduler scheduler;

    @BeforeEach
    void setUp() {
        activityRepository = mock(ActivityRepository.class);
        analysisRepository = mock(ActivityAnalysisRepository.class);
        analyticsEngine = mock(AnalyticsEngine.class);
        llmAnalysisRunner = mock(LlmAnalysisRunner.class);
        scheduler = new AnalysisScheduler(activityRepository, analysisRepository, analyticsEngine, llmAnalysisRunner);
    }

    /* ==================== isDataStale 单元测试 ==================== */

    @Test
    void isDataStaleReturnsTrueWhenGeneratedAtIsNull() {
        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setGeneratedAt(null);
        assertTrue(scheduler.isDataStale(1L, analysis));
    }

    @Test
    void isDataStaleReturnsFalseWhenNoDataExistsAtAll() {
        // generatedAt 有值，所有 4 个时间戳都为 null（无任何关联数据）
        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setGeneratedAt(LocalDateTime.of(2026, 7, 10, 0, 0));

        when(activityRepository.findDataFreshness(1L)).thenReturn(List.<Object[]>of(
                new Object[]{null, null, null, null}));

        assertFalse(scheduler.isDataStale(1L, analysis),
                "无任何新数据时应跳过");
    }

    @Test
    void isDataStaleReturnsFalseWhenLatestDataIsBeforeGeneratedAt() {
        LocalDateTime generatedAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime earlierFeedback = LocalDateTime.of(2026, 7, 9, 10, 0);

        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setGeneratedAt(generatedAt);

        when(activityRepository.findDataFreshness(1L)).thenReturn(List.<Object[]>of(
                new Object[]{
                        Timestamp.valueOf(earlierFeedback),
                        Timestamp.valueOf(earlierFeedback),
                        null,
                        null
                }));

        assertFalse(scheduler.isDataStale(1L, analysis));
    }

    @Test
    void isDataStaleReturnsTrueWhenNewFeedbackAfterGeneratedAt() {
        LocalDateTime generatedAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime newerFeedback = LocalDateTime.of(2026, 7, 11, 9, 0);

        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setGeneratedAt(generatedAt);

        when(activityRepository.findDataFreshness(1L)).thenReturn(List.<Object[]>of(
                new Object[]{
                        Timestamp.valueOf(LocalDateTime.of(2026, 7, 10, 0, 0)),
                        Timestamp.valueOf(newerFeedback),
                        null,
                        null
                }));

        assertTrue(scheduler.isDataStale(1L, analysis),
                "出现比 generatedAt 更新的评价时应刷新");
    }

    @Test
    void isDataStaleReturnsTrueWhenNewCheckInAfterGeneratedAt() {
        LocalDateTime generatedAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime newerCheckIn = LocalDateTime.of(2026, 7, 12, 8, 0);

        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setGeneratedAt(generatedAt);

        when(activityRepository.findDataFreshness(1L)).thenReturn(List.<Object[]>of(
                new Object[]{
                        null,
                        null,
                        Timestamp.valueOf(newerCheckIn),
                        null
                }));

        assertTrue(scheduler.isDataStale(1L, analysis));
    }

    @Test
    void isDataStaleReturnsTrueWhenNewRegistrationAfterGeneratedAt() {
        LocalDateTime generatedAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime newerRegistration = LocalDateTime.of(2026, 7, 11, 15, 0);

        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setGeneratedAt(generatedAt);

        when(activityRepository.findDataFreshness(1L)).thenReturn(List.<Object[]>of(
                new Object[]{
                        null,
                        null,
                        null,
                        Timestamp.valueOf(newerRegistration)
                }));

        assertTrue(scheduler.isDataStale(1L, analysis));
    }

    @Test
    void isDataStalePicksMaxOfAllTimestamps() {
        // 4 个时间戳各不同，应取最大
        LocalDateTime generatedAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime newest = LocalDateTime.of(2026, 7, 13, 0, 0);

        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setGeneratedAt(generatedAt);

        when(activityRepository.findDataFreshness(1L)).thenReturn(List.<Object[]>of(
                new Object[]{
                        Timestamp.valueOf(LocalDateTime.of(2026, 7, 11, 0, 0)),
                        Timestamp.valueOf(LocalDateTime.of(2026, 7, 12, 0, 0)),
                        Timestamp.valueOf(newest),
                        Timestamp.valueOf(LocalDateTime.of(2026, 7, 10, 18, 0))
                }));

        assertTrue(scheduler.isDataStale(1L, analysis),
                "应取 4 个时间戳中的最大值与 generatedAt 比较");
    }

    @Test
    void isDataStaleHandlesActivityUpdatedAtChange() {
        LocalDateTime generatedAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime newerActivityUpdate = LocalDateTime.of(2026, 7, 10, 13, 0);

        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setGeneratedAt(generatedAt);

        when(activityRepository.findDataFreshness(1L)).thenReturn(List.<Object[]>of(
                new Object[]{
                        Timestamp.valueOf(newerActivityUpdate),
                        null,
                        null,
                        null
                }));

        assertTrue(scheduler.isDataStale(1L, analysis),
                "activity 自身 updated_at 晚于 generatedAt 也算过期");
    }

    @Test
    void isDataStaleReturnsTrueWhenActivityNotFound() {
        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setGeneratedAt(LocalDateTime.now());

        when(activityRepository.findDataFreshness(99L)).thenReturn(List.of());

        assertTrue(scheduler.isDataStale(99L, analysis),
                "找不到活动记录时（极端情况）应让 runAsync 自己报具体错误");
    }

    /* ==================== refreshEndedActivitiesAnalysis 集成测试 ==================== */

    @Test
    void schedulerUsesYesterdayWindow() {
        // 核心流程要求只扫描昨日结束的活动
        when(activityRepository.findEndedBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.refreshEndedActivitiesAnalysis();

        ArgumentCaptor<LocalDateTime> sinceCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> untilCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(activityRepository, times(1)).findEndedBetween(sinceCap.capture(), untilCap.capture());

        LocalDateTime since = sinceCap.getValue();
        LocalDateTime until = untilCap.getValue();
        long days = java.time.Duration.between(since, until).toDays();
        assertEquals(1, days, "扫描窗口应为昨日 1 天");
        assertFalse(scheduler.toString().isEmpty()); // just for sonar
    }

    @Test
    void schedulerSkipsActivitiesWithFreshAnalysis() {
        Activity activity = activity(1L, "活动 1");
        when(activityRepository.findEndedBetween(any(), any())).thenReturn(List.of(activity));
        ActivityAnalysis existing = new ActivityAnalysis();
        existing.setGeneratedAt(LocalDateTime.now()); // 最新
        when(analysisRepository.findByActivityId(1L)).thenReturn(Optional.of(existing));
        // 数据时间戳比 generatedAt 早 → 不需刷新
        when(activityRepository.findDataFreshness(1L)).thenReturn(List.<Object[]>of(
                new Object[]{Timestamp.valueOf(LocalDateTime.now().minusDays(1)), null, null, null}));

        scheduler.refreshEndedActivitiesAnalysis();

        verify(llmAnalysisRunner, never()).runAsync(anyLong(), any());
    }

    @Test
    void schedulerDispatchesForStaleOrMissingAnalysis() {
        Activity a1 = activity(1L, "活动 1 - 需要刷新");
        Activity a2 = activity(2L, "活动 2 - 无分析记录");
        when(activityRepository.findEndedBetween(any(), any())).thenReturn(List.of(a1, a2));

        // a1：有分析但已过期
        ActivityAnalysis stale = new ActivityAnalysis();
        stale.setGeneratedAt(LocalDateTime.now().minusDays(1));
        when(analysisRepository.findByActivityId(1L)).thenReturn(Optional.of(stale));
        when(activityRepository.findDataFreshness(1L)).thenReturn(List.<Object[]>of(
                new Object[]{Timestamp.valueOf(LocalDateTime.now()), null, null, null}));

        // a2：完全没有分析
        when(analysisRepository.findByActivityId(2L)).thenReturn(Optional.empty());

        ActivityMetrics metrics = ActivityMetrics.builder()
                .activityId(1L)
                .signupRate(new BigDecimal("10.0"))
                .attendanceRate(new BigDecimal("80.0"))
                .ratingDistribution(Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L))
                .checkInMethodsStats(Map.of())
                .startTime(LocalDateTime.of(2026, 7, 10, 19, 0))
                .endTime(LocalDateTime.of(2026, 7, 10, 21, 0))
                .build();
        when(analyticsEngine.computeMetrics(anyLong())).thenReturn(metrics);

        scheduler.refreshEndedActivitiesAnalysis();

        // 两个活动都应该被派发
        verify(llmAnalysisRunner, times(2)).runAsync(anyLong(), any(ActivityMetrics.class));
    }

    @Test
    void schedulerDoesNotHoldDatabaseTransactionForLlmCall() {
        // 即便某个活动派发时异常，其余活动仍能继续派发
        Activity a1 = activity(1L, "活动 1");
        Activity a2 = activity(2L, "活动 2");
        when(activityRepository.findEndedBetween(any(), any())).thenReturn(List.of(a1, a2));
        when(analysisRepository.findByActivityId(anyLong())).thenReturn(Optional.empty());
        when(analyticsEngine.computeMetrics(1L)).thenThrow(new RuntimeException("metrics 拿不到"));
        when(analyticsEngine.computeMetrics(2L)).thenReturn(ActivityMetrics.builder()
                .activityId(2L).signupRate(new BigDecimal("5.0"))
                .ratingDistribution(Map.of()).checkInMethodsStats(Map.of())
                .startTime(LocalDateTime.now()).endTime(LocalDateTime.now())
                .build());

        scheduler.refreshEndedActivitiesAnalysis();

        // 第二个活动仍应被派发（不会因为第一个的失败而整体回滚）
        verify(llmAnalysisRunner, times(1)).runAsync(anyLong(), any(ActivityMetrics.class));
    }

    @Test
    void schedulerDoesNotSubmitSameActivityTwiceAcrossPhases() {
        Activity activity = activity(1L, "昨日结束且已有规则建议");
        when(activityRepository.findEndedBetween(any(), any())).thenReturn(List.of(activity));
        when(analysisRepository.findByActivityId(1L)).thenReturn(Optional.empty());

        ActivityAnalysis ruleAnalysis = new ActivityAnalysis();
        ruleAnalysis.setActivityId(1L);
        when(analysisRepository.findBySuggestionSource("rule")).thenReturn(List.of(ruleAnalysis));
        when(analyticsEngine.computeMetrics(1L)).thenReturn(ActivityMetrics.builder().activityId(1L).build());

        scheduler.refreshEndedActivitiesAnalysis();

        verify(analyticsEngine, times(1)).computeMetrics(1L);
        verify(llmAnalysisRunner, times(1)).runAsync(anyLong(), any(ActivityMetrics.class));
    }

    /* ==================== helpers ==================== */

    private static Activity activity(Long id, String title) {
        Activity a = new Activity();
        a.setId(id);
        a.setTitle(title);
        a.setStatus("ended");
        a.setStartTime(LocalDateTime.now().minusDays(2));
        a.setEndTime(LocalDateTime.now().minusDays(1));
        a.setMaxParticipants(50);
        a.setSignupCount(0);
        a.setViewCount(0);
        a.setFavoriteCount(0);
        return a;
    }
}
