package com.example.demo.analytics.scheduler;

import com.example.demo.analytics.dto.ActivityMetrics;
import com.example.demo.analytics.dto.SuggestionItem;
import com.example.demo.analytics.entity.ActivityAnalysis;
import com.example.demo.analytics.repository.ActivityAnalysisRepository;
import com.example.demo.analytics.service.AnalyticsEngine;
import com.example.demo.analytics.service.SuggestionGenerator;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 活动分析定时任务
 * <p>
 * 每日凌晨 2:00 自动扫描前一天结束的活动，
 * 调用 {@link AnalyticsEngine} 计算指标并持久化到 {@link ActivityAnalysis}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisScheduler {

    private final ActivityRepository activityRepository;
    private final ActivityAnalysisRepository analysisRepository;
    private final AnalyticsEngine analyticsEngine;
    private final SuggestionGenerator suggestionGenerator;

    /**
     * 每日凌晨 2:00 执行
     * <p>
     * 查询昨天结束、尚未分析的活动，逐条计算指标并保存分析结果。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void analyzeYesterdayEndedActivities() {
        log.info("开始执行每日活动分析任务");

        // 昨天 00:00:00 ~ 今天 00:00:00
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime since = yesterday.atStartOfDay();
        LocalDateTime until = yesterday.plusDays(1).atStartOfDay();

        List<Activity> endedActivities = activityRepository.findEndedBetween(since, until);
        log.info("昨天 ({}) 结束的活动共 {} 个", yesterday, endedActivities.size());

        int success = 0;
        int skipped = 0;

        for (Activity activity : endedActivities) {
            try {
                // 幂等检查：已有分析结果则跳过
                if (analysisRepository.existsByActivityId(activity.getId())) {
                    skipped++;
                    log.debug("活动 {} 已有分析结果，跳过", activity.getId());
                    continue;
                }

                // 计算指标
                ActivityMetrics metrics = analyticsEngine.computeMetrics(activity.getId());

                // 指标 → 实体
                ActivityAnalysis analysis = buildAnalysis(activity, metrics);
                analysisRepository.save(analysis);
                success++;

                log.debug("活动分析完成: id={}, title={}, signupRate={}%, attendanceRate={}%",
                        activity.getId(), activity.getTitle(),
                        metrics.getSignupRate(), metrics.getAttendanceRate());
            } catch (Exception e) {
                log.error("活动分析失败: id={}, title={}, error={}",
                        activity.getId(), activity.getTitle(), e.getMessage(), e);
                // 单活动失败不中断整体任务
            }
        }

        log.info("每日活动分析任务完成: 成功={}, 跳过={}, 总计={}",
                success, skipped, endedActivities.size());
    }

    /**
     * 将指标 DTO 映射为持久化实体，含 LLM 建议
     */
    private ActivityAnalysis buildAnalysis(Activity activity, ActivityMetrics metrics) {
        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setActivity(activity);

        // 核心指标
        analysis.setSignupRate(metrics.getSignupRate());
        analysis.setAttendanceRate(metrics.getAttendanceRate());
        analysis.setAvgRating(metrics.getAvgRating());

        // 指标明细
        analysis.setRatingDistribution(metrics.getRatingDistribution());
        analysis.setCheckInMethodsStats(metrics.getCheckInMethodsStats());

        // 完整指标快照
        var snapshot = new HashMap<String, Object>();
        snapshot.put("viewCount", metrics.getViewCount());
        snapshot.put("signupCount", metrics.getSignupCount());
        snapshot.put("maxParticipants", metrics.getMaxParticipants());
        snapshot.put("approvedCount", metrics.getApprovedCount());
        snapshot.put("checkInCount", metrics.getCheckInCount());
        snapshot.put("feedbackCount", metrics.getFeedbackCount());
        snapshot.put("category", metrics.getCategory());
        snapshot.put("location", metrics.getLocation());
        analysis.setMetricsJson(snapshot);

        // ── LLM 建议生成 ──
        try {
            List<SuggestionItem> suggestions = suggestionGenerator.generateSuggestions(metrics);
            List<Map<String, String>> suggestionMaps = suggestions.stream()
                    .map(s -> {
                        Map<String, String> m = new HashMap<>();
                        m.put("id", s.getId());
                        m.put("category", s.getCategory());
                        m.put("priority", s.getPriority());
                        m.put("content", s.getContent());
                        return m;
                    })
                    .collect(Collectors.toList());
            analysis.setSuggestions((List) suggestionMaps);
            analysis.setSuggestionSource("llm");
            log.info("活动分析 LLM 建议生成成功: activityId={}, 建议数={}",
                    activity.getId(), suggestions.size());
        } catch (Exception e) {
            // LLM 调用失败时，SuggestionGenerator 内部已走降级
            List<SuggestionItem> fallback = suggestionGenerator.fallbackSafe(metrics);
            List<Map<String, String>> fbMaps = fallback.stream()
                    .map(s -> {
                        Map<String, String> m = new HashMap<>();
                        m.put("id", s.getId());
                        m.put("category", s.getCategory());
                        m.put("priority", s.getPriority());
                        m.put("content", s.getContent());
                        return m;
                    })
                    .collect(Collectors.toList());
            analysis.setSuggestions((List) fbMaps);
            analysis.setSuggestionSource("rule");
            log.warn("活动分析 LLM 失败，使用规则降级: activityId={}", activity.getId());
        }

        analysis.setSuggestionModel(null);  // 后续 LLM 调用成功时可改
        analysis.setGeneratedAt(LocalDateTime.now());
        analysis.setCreatedAt(LocalDateTime.now());

        return analysis;
    }
}
