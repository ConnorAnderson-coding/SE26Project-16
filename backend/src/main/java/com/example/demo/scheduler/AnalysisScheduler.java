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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 定时刷新已结束活动的分析结果。
 * <p>
 * 设计要点：
 * <ul>
 *   <li><b>不持数据库事务</b>：整个调度方法不带 {@code @Transactional}，
 *       每个候选活动只用 {@link AnalyticsEngine#computeMetrics(Long)}（{@code @Transactional(readOnly=true)}）
 *       拿快照，并立即通过 {@link LlmAnalysisRunner#runAsync(Long, ActivityMetrics)} 派发到 {@code llmExecutor}</li>
 *   <li><b>跳过条件改为数据新鲜度</b>：拿 {@code analysis.generatedAt} 与
 *       {@link ActivityRepository#findDataFreshness(Long)} 返回的最新数据时间戳比较，
 *       没有新数据就跳过；不再用 {@code createdAt} 与活动结束时间比较</li>
 *   <li><b>限定扫描窗口</b>：只扫 {@code [now - 7 天, now]} 内结束的活动，避免全表扫描历史已结束活动</li>
 *   <li><b>复用异步通道</b>：LLM 调用由 {@link LlmAnalysisRunner} 在专用线程池完成，
 *       避免 LLM 调用持有 DB 连接或被卡进 rollback-only 事务</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisScheduler {

    /** 扫描窗口：只看最近 7 天内结束的活动，避免历史大表全扫。 */
    private static final int REFRESH_WINDOW_DAYS = 7;

    private final ActivityRepository activityRepository;
    private final ActivityAnalysisRepository analysisRepository;
    private final AnalyticsEngine analyticsEngine;
    private final LlmAnalysisRunner llmAnalysisRunner;

    @Scheduled(cron = "0 0 2 * * ?")
    public void refreshEndedActivitiesAnalysis() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(REFRESH_WINDOW_DAYS);
        log.info("开始定时刷新分析结果：扫描窗口 [{} → {}]", since, now);

        List<Activity> candidates = activityRepository.findEndedBetween(since, now);
        log.info("候选活动 {} 个", candidates.size());

        int submitted = 0;
        int skipped = 0;
        int staleFailed = 0;

        for (Activity activity : candidates) {
            try {
                Optional<ActivityAnalysis> existing = analysisRepository.findByActivityId(activity.getId());

                if (existing.isPresent() && !isDataStale(activity.getId(), existing.get())) {
                    skipped++;
                    log.debug("活动 {} 数据无变化，跳过（generatedAt={}）",
                            activity.getId(), existing.get().getGeneratedAt());
                    continue;
                }

                // 立即拿一份 metrics 快照（readOnly 事务，几毫秒），然后立刻异步派发
                ActivityMetrics metrics = analyticsEngine.computeMetrics(activity.getId());
                llmAnalysisRunner.runAsync(activity.getId(), metrics);
                submitted++;
            } catch (Exception e) {
                staleFailed++;
                log.error("派发分析任务失败: id={}, title={}, error={}",
                        activity.getId(), activity.getTitle(), e.getMessage(), e);
            }
        }

        log.info("定时分析调度完成: 派发={}, 跳过={}, 失败={}, 总候选={}",
                submitted, skipped, staleFailed, candidates.size());
    }

    /**
     * 判断"自从上次分析之后，是否有新的数据需要重新分析"。
     * <p>
     * 数据时间戳 = MAX(activity.updated_at, feedback.created_at, check_in.check_in_time, registration.created_at)，
     * 只要其中任何一个比 {@code analysis.generatedAt} 新，就视为过期。
     *
     * @return true = 数据已过期，需要刷新；false = 数据无变化，可跳过
     */
    boolean isDataStale(Long activityId, ActivityAnalysis analysis) {
        if (analysis.getGeneratedAt() == null) {
            return true;
        }
        List<Object[]> rows = activityRepository.findDataFreshness(activityId);
        if (rows.isEmpty() || rows.get(0) == null) {
            // 找不到活动记录（极端情况），当作需要刷新让 runAsync 自己报具体错
            return true;
        }
        Object[] row = rows.get(0);
        LocalDateTime latestData = maxNonNull(
                toLocalDateTime(row[0]),
                toLocalDateTime(row[1]),
                toLocalDateTime(row[2]),
                toLocalDateTime(row[3]));
        // latestData == null 表示四个时间戳都为空（活动自身没动过、也无任何关联数据），
        // 此时没必要再重跑 LLM
        return latestData != null && latestData.isAfter(analysis.getGeneratedAt());
    }

    private static LocalDateTime toLocalDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime ld) {
            return ld;
        }
        if (raw instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (raw instanceof java.util.Date d) {
            return new Timestamp(d.getTime()).toLocalDateTime();
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