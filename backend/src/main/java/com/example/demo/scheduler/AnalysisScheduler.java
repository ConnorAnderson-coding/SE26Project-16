package com.example.demo.scheduler;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityAnalysis;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.service.AnalyticsEngine;
import com.example.demo.service.LlmAnalysisRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 定时刷新已结束活动的分析结果。
 * <p>
 * 设计要点：
 * <ul>
 *   <li><b>昨日结束的活动</b>：每日凌晨扫描前一天结束的活动，首次生成 LLM 建议</li>
 *   <li><b>规则模板升级</b>：同时扫描所有 source="rule" 的历史记录，重新尝试 LLM 生成</li>
 *   <li><b>LLM 固化</b>：source="llm" 的建议不再修改，由 {@link LlmAnalysisRunner} 保证</li>
 *   <li><b>不持数据库事务</b>：整个调度方法不带 {@code @Transactional}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisScheduler {

    private final ActivityRepository activityRepository;
    private final ActivityAnalysisRepository analysisRepository;
    private final AnalyticsEngine analyticsEngine;
    private final LlmAnalysisRunner llmAnalysisRunner;

    @Scheduled(cron = "0 0 2 * * ?")
    public void refreshEndedActivitiesAnalysis() {
        LocalDateTime now = LocalDateTime.now();
        // 昨日 00:00:00 ~ 23:59:59.999...
        LocalDateTime yesterdayStart = now.minusDays(1).toLocalDate().atStartOfDay();
        LocalDateTime yesterdayEnd = yesterdayStart.plusDays(1);

        log.info("开始定时刷新分析结果：扫描昨日结束活动 [{}, {})", yesterdayStart, yesterdayEnd);

        // 1. 扫描昨日结束的活动
        List<Activity> yesterdayEnded = activityRepository.findEndedBetween(yesterdayStart, yesterdayEnd);
        log.info("昨日结束活动 {} 个", yesterdayEnded.size());

        int newSubmitted = 0;
        Set<Long> submittedActivityIds = new HashSet<>();
        for (Activity activity : yesterdayEnded) {
            try {
                Optional<ActivityAnalysis> existing = analysisRepository.findByActivityId(activity.getId());
                if (existing.isPresent() && !isDataStale(activity.getId(), existing.get())) {
                    log.debug("活动 {} 数据无变化，跳过", activity.getId());
                    continue;
                }
                ActivityMetrics metrics = analyticsEngine.computeMetrics(activity.getId());
                llmAnalysisRunner.runAsync(activity.getId(), metrics);
                submittedActivityIds.add(activity.getId());
                newSubmitted++;
            } catch (Exception e) {
                log.error("派发分析任务失败: id={}, title={}, error={}",
                        activity.getId(), activity.getTitle(), e.getMessage(), e);
            }
        }

        // 2. 扫描规则模板记录，尝试升级为 LLM 建议
        List<ActivityAnalysis> ruleBased = analysisRepository.findBySuggestionSource("rule");
        log.info("规则模板记录 {} 条，尝试升级为 LLM 建议", ruleBased.size());

        int upgradeSubmitted = 0;
        for (ActivityAnalysis analysis : ruleBased) {
            try {
                Long activityId = analysis.getActivityId();
                if (!submittedActivityIds.add(activityId)) {
                    log.debug("活动 {} 已在昨日结束阶段派发，跳过重复升级任务", activityId);
                    continue;
                }
                ActivityMetrics metrics = analyticsEngine.computeMetrics(activityId);
                llmAnalysisRunner.runAsync(activityId, metrics);
                upgradeSubmitted++;
            } catch (Exception e) {
                log.error("规则升级派发失败: analysisId={}, error={}",
                        analysis.getId(), e.getMessage(), e);
            }
        }

        log.info("定时分析调度完成: 新活动={}, 规则升级={}",
                newSubmitted, upgradeSubmitted);
    }

    /**
     * 判断上次分析后是否有活动、评价、签到或报名数据更新。
     */
    boolean isDataStale(Long activityId, ActivityAnalysis analysis) {
        if (analysis.getGeneratedAt() == null) {
            return true;
        }
        List<Object[]> rows = activityRepository.findDataFreshness(activityId);
        if (rows.isEmpty() || rows.get(0) == null) {
            return true;
        }
        Object[] row = rows.get(0);
        LocalDateTime latestData = maxNonNull(
                toLocalDateTime(row[0]),
                toLocalDateTime(row[1]),
                toLocalDateTime(row[2]),
                toLocalDateTime(row[3]));
        return latestData != null && latestData.isAfter(analysis.getGeneratedAt());
    }

    private static LocalDateTime toLocalDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (raw instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (raw instanceof java.util.Date date) {
            return new Timestamp(date.getTime()).toLocalDateTime();
        }
        throw new IllegalArgumentException("无法识别的时间类型: " + raw.getClass().getName());
    }

    private static LocalDateTime maxNonNull(LocalDateTime... values) {
        return java.util.Arrays.stream(values)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
