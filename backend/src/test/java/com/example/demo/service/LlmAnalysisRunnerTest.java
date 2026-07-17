package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.dto.analytics.SuggestionItem;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityAnalysis;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.ActivityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 锁定 {@link LlmAnalysisRunner} 的状态机：
 * <ul>
 *   <li>初始：先写 pending</li>
 *   <li>LLM 成功：覆盖为 ready + suggestionModel</li>
 *   <li>LLM 失败：写入规则模板 + suggestionSource=rule + failureReason</li>
 *   <li>规则模板也炸：写入 failed + failureReason</li>
 * </ul>
 * <p>
 * 由于 {@code save()} 多次写同一对象引用，测试用 JSON 深拷贝快照每一次 save 时的状态，
 * 以便断言 pending → ready 的中间态。
 */
class LlmAnalysisRunnerTest {

    private SuggestionGenerator suggestionGenerator;
    private LlmClient llmClient;
    private ActivityAnalysisRepository analysisRepository;
    private ActivityRepository activityRepository;
    private LlmAnalysisRunner runner;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        suggestionGenerator = mock(SuggestionGenerator.class);
        llmClient = mock(LlmClient.class);
        analysisRepository = mock(ActivityAnalysisRepository.class);
        activityRepository = mock(ActivityRepository.class);
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        runner = new LlmAnalysisRunner(suggestionGenerator, llmClient, analysisRepository, activityRepository);

        Activity activity = new Activity();
        activity.setId(1L);
        activity.setTitle("测试活动");
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(llmClient.getModel()).thenReturn("deepseek-chat");
    }

    @Test
    void successfulLlmCallTransitionsToReady() {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        // 用深拷贝记录每次 save 时的状态快照
        java.util.concurrent.atomic.AtomicReference<ActivityAnalysis> lastSaved = new java.util.concurrent.atomic.AtomicReference<>();
        when(analysisRepository.save(any(ActivityAnalysis.class)))
                .thenAnswer(inv -> {
                    ActivityAnalysis a = inv.getArgument(0);
                    snapshots.add(toMap(a));
                    lastSaved.set(a);
                    return a;
                });
        when(analysisRepository.findByActivityId(1L)).thenAnswer(inv -> Optional.ofNullable(lastSaved.get()));

        when(suggestionGenerator.generateSuggestions(any()))
                .thenReturn(List.of(
                        SuggestionItem.builder().id("1").category("promotion").priority("high").content("p").build(),
                        SuggestionItem.builder().id("2").category("schedule").priority("medium").content("s").build(),
                        SuggestionItem.builder().id("3").category("venue").priority("low").content("v").build(),
                        SuggestionItem.builder().id("4").category("content").priority("medium").content("c").build()
                ));

        runner.runAsync(1L, metrics());

        verify(analysisRepository, times(2)).save(any(ActivityAnalysis.class));
        assertEquals(2, snapshots.size());

        Map<String, Object> pending = snapshots.get(0);
        assertEquals("pending", pending.get("analysisStatus"));
        assertEquals("pending", pending.get("suggestionSource"));
        assertNull(pending.get("suggestionModel"));

        Map<String, Object> ready = snapshots.get(1);
        assertEquals("ready", ready.get("analysisStatus"));
        assertEquals("llm", ready.get("suggestionSource"));
        assertEquals("deepseek-chat", ready.get("suggestionModel"));
        assertNotNull(ready.get("suggestions"));
        assertNull(ready.get("failureReason"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> suggestions = (List<Map<String, String>>) ready.get("suggestions");
        assertEquals(4, suggestions.size());
    }

    @Test
    void llmFailureFallsBackToRuleAndWritesFailureReason() {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<ActivityAnalysis> lastSaved = new java.util.concurrent.atomic.AtomicReference<>();
        when(analysisRepository.save(any(ActivityAnalysis.class)))
                .thenAnswer(inv -> {
                    ActivityAnalysis a = inv.getArgument(0);
                    snapshots.add(toMap(a));
                    lastSaved.set(a);
                    return a;
                });
        when(analysisRepository.findByActivityId(1L)).thenAnswer(inv -> Optional.ofNullable(lastSaved.get()));

        when(suggestionGenerator.generateSuggestions(any()))
                .thenThrow(new LlmClient.LlmCallException("LLM 401 不可重试", false));
        when(suggestionGenerator.fallbackSafe(any()))
                .thenReturn(List.of(
                        SuggestionItem.builder().id("1").category("promotion").priority("high").content("p").build(),
                        SuggestionItem.builder().id("2").category("schedule").priority("medium").content("s").build(),
                        SuggestionItem.builder().id("3").category("venue").priority("low").content("v").build(),
                        SuggestionItem.builder().id("4").category("content").priority("medium").content("c").build()
                ));

        runner.runAsync(1L, metrics());

        verify(analysisRepository, times(2)).save(any(ActivityAnalysis.class));
        Map<String, Object> finalState = snapshots.get(1);
        assertEquals("ready", finalState.get("analysisStatus"), "规则模板兜底后状态应为 ready");
        assertEquals("rule", finalState.get("suggestionSource"));
        assertEquals("deepseek-chat", finalState.get("suggestionModel"), "仍记录请求的模型，便于审计");
        String reason = (String) finalState.get("failureReason");
        assertNotNull(reason, "失败原因必须留痕");
        assertTrue(reason.contains("LlmCallException"));
    }

    @Test
    void ruleFallbackAlsoFailingMarksFailed() {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<ActivityAnalysis> lastSaved = new java.util.concurrent.atomic.AtomicReference<>();
        when(analysisRepository.save(any(ActivityAnalysis.class)))
                .thenAnswer(inv -> {
                    ActivityAnalysis a = inv.getArgument(0);
                    snapshots.add(toMap(a));
                    lastSaved.set(a);
                    return a;
                });
        when(analysisRepository.findByActivityId(1L)).thenAnswer(inv -> Optional.ofNullable(lastSaved.get()));

        when(suggestionGenerator.generateSuggestions(any()))
                .thenThrow(new LlmClient.LlmCallException("LLM 5xx", true));
        when(suggestionGenerator.fallbackSafe(any()))
                .thenThrow(new RuntimeException("suggestion builder 抛异常"));

        runner.runAsync(1L, metrics());

        verify(analysisRepository, times(2)).save(any(ActivityAnalysis.class));
        Map<String, Object> finalState = snapshots.get(1);
        assertEquals("failed", finalState.get("analysisStatus"));
        // LLM 与规则模板兜底都失败时，source 必须如实记录为 failed，避免前端按"规则模板"标签渲染但内容为空
        assertEquals("failed", finalState.get("suggestionSource"));
        String reason = (String) finalState.get("failureReason");
        assertNotNull(reason);
        assertTrue(reason.contains("fallback_failed"));
    }

    @Test
    void pendingIsWrittenBeforeLlmStarts() {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<ActivityAnalysis> lastSaved = new java.util.concurrent.atomic.AtomicReference<>();
        when(analysisRepository.save(any(ActivityAnalysis.class)))
                .thenAnswer(inv -> {
                    ActivityAnalysis a = inv.getArgument(0);
                    snapshots.add(toMap(a));
                    lastSaved.set(a);
                    return a;
                });
        when(analysisRepository.findByActivityId(1L)).thenAnswer(inv -> Optional.ofNullable(lastSaved.get()));

        when(suggestionGenerator.generateSuggestions(any()))
                .thenReturn(List.of(
                        SuggestionItem.builder().id("1").category("promotion").priority("high").content("p").build(),
                        SuggestionItem.builder().id("2").category("schedule").priority("medium").content("s").build(),
                        SuggestionItem.builder().id("3").category("venue").priority("low").content("v").build(),
                        SuggestionItem.builder().id("4").category("content").priority("medium").content("c").build()
                ));

        runner.runAsync(1L, metrics());

        verify(analysisRepository, times(2)).save(any(ActivityAnalysis.class));
        // 第一次落盘必须是 pending，前端轮询时能看到"任务已接收"
        Map<String, Object> pending = snapshots.get(0);
        assertEquals("pending", pending.get("analysisStatus"));
        assertNotNull(pending.get("generatedAt"));
    }

    @Test
    void failedRuleUpgradePreservesExistingSuggestion() {
        ActivityAnalysis existing = new ActivityAnalysis();
        existing.setActivityId(1L);
        existing.setSuggestionSource("rule");
        existing.setAnalysisStatus("ready");
        existing.setSuggestions(List.of(Map.of(
                "id", "old-1",
                "category", "promotion",
                "priority", "high",
                "content", "保留原建议")));
        when(analysisRepository.findByActivityId(1L)).thenReturn(Optional.of(existing));
        when(suggestionGenerator.generateSuggestions(any()))
                .thenThrow(new LlmClient.LlmCallException("LLM unavailable", true));

        runner.runAsync(1L, metrics());

        verify(analysisRepository, never()).save(any(ActivityAnalysis.class));
        verify(suggestionGenerator, never()).fallbackSafe(any());
        assertEquals("rule", existing.getSuggestionSource());
        assertEquals("ready", existing.getAnalysisStatus());
        assertEquals("保留原建议", existing.getSuggestions().getFirst().get("content"));
    }

    /* ==================== helpers ==================== */

    /**
     * 把 ActivityAnalysis 通过 JSON 序列化拍扁成 Map，绕开 Hibernate 懒加载和实体引用复用问题，
     * 这样每次 save 时的状态都能独立被断言。
     */
    private Map<String, Object> toMap(ActivityAnalysis a) {
        try {
            String json = mapper.writeValueAsString(a);
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("snapshot 失败", e);
        }
    }

    private static ActivityMetrics metrics() {
        return ActivityMetrics.builder()
                .activityId(1L)
                .activityTitle("测试活动")
                .signupRate(new BigDecimal("35.0"))
                .attendanceRate(new BigDecimal("80.0"))
                .ratingDistribution(Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L))
                .checkInMethodsStats(Map.of())
                .startTime(LocalDateTime.of(2026, 7, 10, 19, 0))
                .endTime(LocalDateTime.of(2026, 7, 10, 21, 0))
                .build();
    }
}
