package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.BusinessException;
import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.dto.analytics.SuggestionItem;
import com.example.demo.entity.ActivityAnalysis;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.service.AnalyticsEngine;
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsEngine analyticsEngine;
    private final ActivityAnalysisRepository analysisRepository;
    private final ActivityRepository activityRepository;

    @GetMapping("/activity/{activityId}/metrics")
    public ApiResponse<ActivityMetrics> getActivityMetrics(@PathVariable Long activityId) {
        assertOrganizer(activityId);
        ActivityMetrics metrics = analyticsEngine.computeMetrics(activityId);
        return ApiResponse.ok(metrics);
    }

    @GetMapping("/activity/{activityId}")
    public ApiResponse<Map<String, Object>> getFullAnalysis(@PathVariable Long activityId) {
        assertOrganizer(activityId);
        ActivityMetrics metrics = analyticsEngine.computeMetrics(activityId);

        Optional<ActivityAnalysis> saved = analysisRepository.findByActivityId(activityId);

        Map<String, Object> result = new HashMap<>();
        result.put("activityId", activityId);
        result.put("metrics", metrics);
        if (saved.isPresent()) {
            ActivityAnalysis a = saved.get();
            result.put("suggestions", convertSuggestions(a));
            result.put("suggestionSource", a.getSuggestionSource());
            result.put("suggestionModel", a.getSuggestionModel());
            result.put("analysisStatus", a.getAnalysisStatus());
            result.put("failureReason", a.getFailureReason());
            result.put("generatedAt", a.getGeneratedAt());
        } else {
            result.put("suggestions", List.of());
            result.put("suggestionSource", "none");
            result.put("suggestionModel", null);
            result.put("analysisStatus", "none");
            result.put("failureReason", null);
            result.put("generatedAt", null);
        }
        return ApiResponse.ok(result);
    }

    @SuppressWarnings("unchecked")
    private List<SuggestionItem> convertSuggestions(ActivityAnalysis analysis) {
        if (analysis.getSuggestions() == null) {
            return List.of();
        }
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
