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

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAnalysisRunner {

    private final SuggestionGenerator suggestionGenerator;
    private final LlmClient llmClient;
    private final ActivityAnalysisRepository analysisRepository;
    private final ActivityRepository activityRepository;

    
    @Async("llmExecutor")
    public void runAsync(Long activityId, ActivityMetrics metrics) {
        log.info("[异步分析] 开始 activityId={}", activityId);

        // 检查是否已有 LLM 生成的固化建议
        var existing = analysisRepository.findByActivityId(activityId);
        if (existing.isPresent()
                && "llm".equals(existing.get().getSuggestionSource())
                && "ready".equals(existing.get().getAnalysisStatus())) {
            log.info("[异步分析] 活动 {} 已有 LLM 固化建议，跳过", activityId);
            return;
        }

        // 记录是否为规则模板升级（用于日志区分）
        boolean isUpgrade = existing.isPresent() && "rule".equals(existing.get().getSuggestionSource());
        if (isUpgrade) {
            log.info("[异步分析] 活动 {} 规则模板升级为 LLM 建议", activityId);
        }

        // 首次生成写 pending；规则升级失败时要求原记录完全不变，因此升级阶段不覆盖旧状态
        if (!isUpgrade) {
            persistPending(activityId, metrics);
        }

        try {
            List<SuggestionItem> suggestions = suggestionGenerator.generateSuggestions(metrics);
            persistFinal(activityId, metrics, suggestions, "llm", llmClient.getModel(), null);
            log.info("[异步分析] LLM 成功 activityId={}, 建议数={}, 类型={}",
                    activityId, suggestions.size(), isUpgrade ? "规则升级" : "首次生成");
        } catch (Exception e) {
            // LLM 失败：若已有规则模板则保留，否则写入规则模板兜底
            if (existing.isPresent() && "rule".equals(existing.get().getSuggestionSource())) {
                log.warn("[异步分析] LLM 升级失败，保留已有规则模板 activityId={}: {}", activityId, e.getMessage());
                return;
            }
            log.warn("[异步分析] LLM 失败转规则模板 activityId={}: {}", activityId, e.getMessage());
            try {
                List<SuggestionItem> fallback = suggestionGenerator.fallbackSafe(metrics);
                String reason = e.getClass().getSimpleName() + ": " + truncate(e.getMessage(), 480);
                persistFinal(activityId, metrics, fallback, "rule", llmClient.getModel(), reason);
            } catch (Exception fallbackError) {
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
