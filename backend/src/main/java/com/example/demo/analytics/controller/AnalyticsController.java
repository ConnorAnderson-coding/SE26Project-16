package com.example.demo.analytics.controller;

import com.example.demo.analytics.dto.ActivityMetrics;
import com.example.demo.analytics.dto.SuggestionItem;
import com.example.demo.analytics.entity.ActivityAnalysis;
import com.example.demo.analytics.repository.ActivityAnalysisRepository;
import com.example.demo.analytics.service.AnalyticsEngine;
import com.example.demo.analytics.service.SuggestionGenerator;
import com.example.demo.common.ApiResponse;
import com.example.demo.common.BusinessException;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析接口
 * <p>
 * 提供活动指标查询、完整分析结果及手动触发分析接口。
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsEngine analyticsEngine;
    private final SuggestionGenerator suggestionGenerator;
    private final ActivityAnalysisRepository analysisRepository;
    private final ActivityRepository activityRepository;

    /**
     * 获取单个活动的全部核心指标（实时计算，含 Redis 缓存）
     */
    @GetMapping("/activity/{activityId}/metrics")
    public ApiResponse<ActivityMetrics> getActivityMetrics(@PathVariable Long activityId) {
        assertOrganizer(activityId);
        ActivityMetrics metrics = analyticsEngine.computeMetrics(activityId);
        return ApiResponse.ok(metrics);
    }

    /**
     * 获取活动的完整分析结果（指标 + 已有的 LLM 建议）
     * <p>
     * 先从 activity_analysis 表读取已有建议，无记录时仅返回指标。
     * LLM 建议生成由定时任务或手动触发完成，不在本接口同步调用。
     */
    @GetMapping("/activity/{activityId}")
    public ApiResponse<Map<String, Object>> getFullAnalysis(@PathVariable Long activityId) {
        assertOrganizer(activityId);
        ActivityMetrics metrics = analyticsEngine.computeMetrics(activityId);

        // 不再返回缓存的旧建议，前端通过点击按钮实时调用 LLM 生成
        Map<String, Object> result = new HashMap<>();
        result.put("activityId", activityId);
        result.put("metrics", metrics);
        result.put("suggestions", List.of());
        result.put("suggestionSource", "none");
        return ApiResponse.ok(result);
    }

    /**
     * 手动触发单个活动的分析（指标计算 + LLM 建议生成），结果持久化到数据库
     */
    @PostMapping("/trigger/{activityId}")
    public ApiResponse<Map<String, Object>> triggerAnalysis(@PathVariable Long activityId) {
        assertOrganizer(activityId);
        ActivityMetrics metrics = analyticsEngine.computeMetrics(activityId);
        List<SuggestionItem> suggestions;
        String source;
        try {
            suggestions = suggestionGenerator.generateSuggestions(metrics);
            source = "llm";
        } catch (Exception e) {
            suggestions = suggestionGenerator.fallbackSafe(metrics);
            source = "rule";
        }

        // 持久化到数据库
        ActivityAnalysis analysis = analysisRepository.findByActivityId(activityId).orElse(new ActivityAnalysis());
        analysis.setActivity(activityRepository.findById(activityId).orElseThrow());
        analysis.setSignupRate(metrics.getSignupRate());
        analysis.setAttendanceRate(metrics.getAttendanceRate());
        analysis.setAvgRating(metrics.getAvgRating());
        analysis.setRatingDistribution(metrics.getRatingDistribution());
        analysis.setCheckInMethodsStats(metrics.getCheckInMethodsStats());
        analysis.setSuggestions((List) suggestions.stream().map(s -> {
            Map<String, String> m = new HashMap<>();
            m.put("id", s.getId()); m.put("category", s.getCategory());
            m.put("priority", s.getPriority()); m.put("content", s.getContent());
            return m;
        }).toList());
        analysis.setSuggestionSource(source);
        analysis.setGeneratedAt(LocalDateTime.now());
        if (analysis.getCreatedAt() == null) analysis.setCreatedAt(LocalDateTime.now());
        analysisRepository.save(analysis);

        Map<String, Object> result = new HashMap<>();
        result.put("activityId", activityId);
        result.put("metrics", metrics);
        result.put("suggestions", suggestions);
        result.put("suggestionSource", source);
        return ApiResponse.ok(result);
    }

    @SuppressWarnings("unchecked")
    private List<SuggestionItem> convertSuggestions(ActivityAnalysis analysis) {
        if (analysis.getSuggestions() == null) return List.of();
        return ((List<Map<String, String>>) (List<?>) analysis.getSuggestions()).stream()
                .map(m -> SuggestionItem.builder()
                        .id(m.getOrDefault("id", ""))
                        .category(m.getOrDefault("category", "other"))
                        .priority(m.getOrDefault("priority", "medium"))
                        .content(m.getOrDefault("content", ""))
                        .build())
                .toList();
    }

    private void assertOrganizer(Long activityId) {
        var activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        if (!SecurityUtils.getCurrentUserId().equals(activity.getOrganizerId())) {
            throw new BusinessException(403, "仅活动组织者可以查看或触发分析");
        }
    }
}
