package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.BusinessException;
import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.dto.analytics.SuggestionItem;
import com.example.demo.entity.ActivityAnalysis;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.scheduler.AnalysisScheduler;
import com.example.demo.service.AnalyticsEngine;
import com.example.demo.service.LlmAnalysisRunner;
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final LlmAnalysisRunner llmAnalysisRunner;
    private final AnalysisScheduler analysisScheduler;

    /** 仅测试用：默认关闭。启动时设 app.analytics.manual-trigger=true 可手动触发 LLM/调度。 */
    @Value("${app.analytics.manual-trigger:false}")
    private boolean manualTriggerEnabled;

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

    /**
     * 测试接口：对指定活动异步触发一次分析（LLM，失败则规则兜底）。
     * 需 app.analytics.manual-trigger=true，且当前用户为该活动组织者。
     */
    @PostMapping("/activity/{activityId}/generate")
    public ApiResponse<Map<String, Object>> triggerGenerate(@PathVariable Long activityId) {
        assertManualTriggerEnabled();
        assertOrganizer(activityId);
        ActivityMetrics metrics = analyticsEngine.computeMetrics(activityId);
        llmAnalysisRunner.runAsync(activityId, metrics);
        Map<String, Object> body = new HashMap<>();
        body.put("activityId", activityId);
        body.put("submitted", true);
        body.put("message", "分析任务已异步提交，请稍后查询 /activity/{id}");
        return ApiResponse.ok(body);
    }

    /**
     * 测试接口：立即执行与每日 02:00 相同的定时分析调度逻辑。
     */
    @PostMapping("/run-scheduler")
    public ApiResponse<Map<String, Object>> triggerScheduler() {
        assertManualTriggerEnabled();
        // 仅登录用户可调；调度扫描全站昨日结束活动，不绑定组织者
        if (SecurityUtils.getCurrentUserId() == null) {
            throw new BusinessException(401, "未登录");
        }
        analysisScheduler.refreshEndedActivitiesAnalysis();
        Map<String, Object> body = new HashMap<>();
        body.put("submitted", true);
        body.put("message", "已同步执行 refreshEndedActivitiesAnalysis()");
        return ApiResponse.ok(body);
    }

    private void assertManualTriggerEnabled() {
        if (!manualTriggerEnabled) {
            throw new BusinessException(403, "手动触发未启用（app.analytics.manual-trigger=false）");
        }
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

    @Transactional(readOnly = true)
    private void assertOrganizer(Long activityId) {
        var activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        String organizerId = activity.getOrganizer() != null
                ? activity.getOrganizer().getId()
                : activity.getOrganizerId();
        if (!SecurityUtils.getCurrentUserId().equals(organizerId)) {
            throw new BusinessException(403, "仅活动组织者可以查看或触发分析");
        }
    }

}
