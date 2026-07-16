package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.dto.analytics.SuggestionItem;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityAnalysis;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在专用线程池中异步执行"LLM 改进建议生成 + 落库"。
 * <p>
 * 设计目的：
 * <ul>
 *   <li>让 {@code POST /analytics/trigger/{id}} 不再阻塞 Web 请求线程</li>
 *   <li>区分 LLM 失败 vs LLM 成功，统一推进 {@link ActivityAnalysis#analysisStatus} 状态</li>
 *   <li>写入 {@code suggestion_model}，便于审计实际调用的模型</li>
 * </ul>
 * <p>
 * Controller 调用本服务的 {@link #runAsync} 后立即返回 202；
 * 前端通过轮询 {@code GET /analytics/activity/{id}} 拉取最新状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAnalysisRunner {

    private final SuggestionGenerator suggestionGenerator;
    private final LlmClient llmClient;
    private final ActivityAnalysisRepository analysisRepository;
    private final ActivityRepository activityRepository;

    /**
     * 异步执行分析任务。在专用 {@code llmExecutor} 线程池上执行，
     * 调用方线程不会因为 LLM 调用慢而被阻塞。
     */
    @Async("llmExecutor")
    public void runAsync(Long activityId, ActivityMetrics metrics) {
        log.info("[异步分析] 开始 activityId={}", activityId);

        // 先把 pending 状态写盘，让前端轮询时能立刻看到"已提交"
        persistPending(activityId, metrics);

        try {
            List<SuggestionItem> suggestions = suggestionGenerator.generateSuggestions(metrics);
            persistFinal(activityId, metrics, suggestions, "llm", llmClient.getModel(), null);
            log.info("[异步分析] LLM 成功 activityId={}, 建议数={}", activityId, suggestions.size());
        } catch (Exception e) {
            // LLM 不可恢复地失败：写规则模板兜底 + 标记 failed
            log.warn("[异步分析] LLM 失败转规则模板 activityId={}: {}", activityId, e.getMessage());
            try {
                List<SuggestionItem> fallback = suggestionGenerator.fallbackSafe(metrics);
                String reason = e.getClass().getSimpleName() + ": " + truncate(e.getMessage(), 480);
                persistFinal(activityId, metrics, fallback, "rule", llmClient.getModel(), reason);
            } catch (Exception fallbackError) {
                // 连规则模板都炸了（极少见）：标记 failed，把异常写进 failure_reason
                log.error("[异步分析] 兜底规则模板也失败 activityId={}", activityId, fallbackError);
                String reason = "fallback_failed: " + fallbackError.getClass().getSimpleName()
                        + ": " + truncate(fallbackError.getMessage(), 450);
                markFailed(activityId, metrics, reason);
            }
        }
    }

    private void persistPending(Long activityId, ActivityMetrics metrics) {
        ActivityAnalysis analysis = analysisRepository.findByActivityId(activityId).orElse(new ActivityAnalysis());
        if (analysis.getCreatedAt() == null) {
            analysis.setCreatedAt(LocalDateTime.now());
        }
        analysis.setActivity(activityRepository.findById(activityId).orElseThrow());
        // pending 阶段不写 suggestions，等成功后覆盖
        analysis.setSignupRate(metrics.getSignupRate());
        analysis.setAttendanceRate(metrics.getAttendanceRate());
        analysis.setAvgRating(metrics.getAvgRating());
        analysis.setRatingDistribution(metrics.getRatingDistribution());
        analysis.setCheckInMethodsStats(metrics.getCheckInMethodsStats());
        analysis.setAnalysisStatus("pending");
        analysis.setSuggestionSource("pending");
        analysis.setSuggestionModel(null);
        analysis.setFailureReason(null);
        analysis.setGeneratedAt(LocalDateTime.now());
        analysisRepository.save(analysis);
    }

    private void persistFinal(Long activityId,
                              ActivityMetrics metrics,
                              List<SuggestionItem> suggestions,
                              String source,
                              String model,
                              String failureReason) {
        // 重新从 DB 加载，避免和 persistPending 共用同一对象引用导致后续测试 / 审计读不到中间状态
        ActivityAnalysis analysis = analysisRepository.findByActivityId(activityId).orElse(new ActivityAnalysis());
        if (analysis.getCreatedAt() == null) {
            analysis.setCreatedAt(LocalDateTime.now());
        }
        if (analysis.getActivity() == null) {
            analysis.setActivity(activityRepository.findById(activityId).orElseThrow());
        }
        analysis.setSignupRate(metrics.getSignupRate());
        analysis.setAttendanceRate(metrics.getAttendanceRate());
        analysis.setAvgRating(metrics.getAvgRating());
        analysis.setRatingDistribution(metrics.getRatingDistribution());
        analysis.setCheckInMethodsStats(metrics.getCheckInMethodsStats());
        analysis.setSuggestions((List) suggestions.stream().map(s -> {
            Map<String, String> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("category", s.getCategory());
            m.put("priority", s.getPriority());
            m.put("content", s.getContent());
            return m;
        }).toList());
        analysis.setSuggestionSource(source);
        analysis.setSuggestionModel(model);
        analysis.setAnalysisStatus("ready");
        analysis.setFailureReason(failureReason);
        analysis.setGeneratedAt(LocalDateTime.now());
        analysisRepository.save(analysis);
    }

    private void markFailed(Long activityId, ActivityMetrics metrics, String failureReason) {
        ActivityAnalysis analysis = analysisRepository.findByActivityId(activityId).orElse(new ActivityAnalysis());
        if (analysis.getActivity() == null) {
            analysis.setActivity(activityRepository.findById(activityId).orElseThrow());
        }
        analysis.setSignupRate(metrics.getSignupRate());
        analysis.setAttendanceRate(metrics.getAttendanceRate());
        analysis.setAvgRating(metrics.getAvgRating());
        analysis.setSuggestionSource("rule");
        analysis.setSuggestionModel(null);
        analysis.setAnalysisStatus("failed");
        analysis.setFailureReason(failureReason);
        analysis.setGeneratedAt(LocalDateTime.now());
        analysisRepository.save(analysis);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}