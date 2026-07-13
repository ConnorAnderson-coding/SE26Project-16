package com.example.demo.analytics.service;

import com.example.demo.analytics.dto.ActivityMetrics;
import com.example.demo.analytics.repository.CheckInRepository;
import com.example.demo.common.CacheNames;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.FeedbackRepository;
import com.example.demo.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标计算引擎
 * <p>
 * 负责从数据库统计活动的核心指标：
 * 浏览量、报名人数、签到人数、平均评分、评分分布等。
 */
@Service
@RequiredArgsConstructor
public class AnalyticsEngine {

    private final ActivityRepository activityRepository;
    private final RegistrationRepository registrationRepository;
    private final CheckInRepository checkInRepository;
    private final FeedbackRepository feedbackRepository;

    /**
     * 计算单个活动的全部指标
     * <p>
     * 结果缓存于 Redis，key 格式: {@code campus:analytics:activity::{activityId}}，TTL 6 小时。
     * 缓存未命中时才查询数据库进行实时计算。
     *
     * @param activityId 活动ID
     * @return 活动指标对象，包含所有核心维度的统计数据
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.ANALYTICS_ACTIVITY, key = "#activityId",
            unless = "#result == null")
    public ActivityMetrics computeMetrics(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("活动不存在: " + activityId));

        // ── 基础数据拉取 ──
        // 报名相关
        long approvedCount = registrationRepository.countByActivityIdAndStatus(
                activityId, "approved");

        // 签到相关
        long checkInCount = checkInRepository.countByActivityId(activityId);
        Map<String, Long> checkInMethodsStats = computeCheckInMethodStats(activityId);

        // 评价相关
        var feedbackList = feedbackRepository.findByActivityIdOrderByCreatedAtDesc(activityId);
        long feedbackCount = feedbackList.size();
        BigDecimal avgRating = computeAvgRating(activityId);
        Map<Integer, Long> ratingDistribution = computeRatingDistribution(activityId);
        // 脱敏：仅保留评分和内容，不关联用户ID/姓名
        List<String> feedbackContents = feedbackList.stream()
                .map(f -> f.getRating() + "分:" + sanitizeFeedback(f.getContent()))
                .toList();

        // ── 比率计算 ──
        // 报名转化率 = 报名人数 / 浏览量
        long signupCount = activity.getSignupCount() == null ? 0 : activity.getSignupCount();
        BigDecimal signupRate = calcRate(signupCount,
                activity.getViewCount() == null ? 0 : activity.getViewCount());
        // 到场率 = 签到人数 / 报名人数
        BigDecimal attendanceRate = calcRate(checkInCount, signupCount);

        return ActivityMetrics.builder()
                .activityId(activity.getId())
                .activityTitle(activity.getTitle())
                .category(activity.getCategory())
                .location(activity.getLocation())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                // 核心指标
                .viewCount(activity.getViewCount())
                .signupCount(activity.getSignupCount())
                .maxParticipants(activity.getMaxParticipants())
                .signupRate(signupRate)
                .approvedCount(approvedCount)
                .checkInCount(checkInCount)
                .attendanceRate(attendanceRate)
                .favoriteCount(activity.getFavoriteCount())
                // 评分指标
                .feedbackCount(feedbackCount)
                .avgRating(avgRating)
                .ratingDistribution(ratingDistribution)
                // 签到方式
                .checkInMethodsStats(checkInMethodsStats)
                // 评价内容（已脱敏）
                .feedbackContents(feedbackContents)
                .build();
    }

    /**
     * 计算平均评分
     */
    private BigDecimal computeAvgRating(Long activityId) {
        var feedbacks = feedbackRepository.findByActivityIdOrderByCreatedAtDesc(activityId);
        if (feedbacks.isEmpty()) {
            return null;
        }
        double avg = feedbacks.stream()
                .mapToInt(f -> f.getRating())
                .average()
                .orElse(0);
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算评分分布 (1-5 星各多少人)
     */
    private Map<Integer, Long> computeRatingDistribution(Long activityId) {
        var feedbacks = feedbackRepository.findByActivityIdOrderByCreatedAtDesc(activityId);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        // 初始化 1-5 星
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        feedbacks.forEach(f -> {
            int rating = f.getRating();
            distribution.merge(rating, 1L, Long::sum);
        });
        return distribution;
    }

    /**
     * 计算签到方式分布
     */
    private Map<String, Long> computeCheckInMethodStats(Long activityId) {
        List<Object[]> rows = checkInRepository.countByMethodGroupByActivityId(activityId);
        Map<String, Long> stats = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String method = (String) row[0];
            Long count = (Long) row[1];
            stats.put(method, count);
        }
        return stats;
    }

    /**
     * 比率计算，分子或分母为 0 时返回 0
     */
    private BigDecimal calcRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    /**
     * 在评价正文发送给外部 LLM 前屏蔽常见个人标识信息。
     * 用户ID和姓名本就不会进入指标 DTO；这里进一步处理正文中主动填写的联系方式和编号。
     */
    static String sanitizeFeedback(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content
                .replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[邮箱已脱敏]")
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "[手机号已脱敏]")
                .replaceAll("(?<!\\d)\\d{8,18}(?!\\d)", "[编号已脱敏]");
    }

    /**
     * 扩充辅助维度：同类活动历史平均到场率
     */
    @Transactional(readOnly = true)
    public BigDecimal getCategoryAvgAttendanceRate(String category) {
        // 此方法预留扩展，后续通过 JPA Native Query 实现
        // 当前返回 null 表示无对比数据
        return null;
    }

    /**
     * 扩充辅助维度：同类活动历史平均评分
     */
    @Transactional(readOnly = true)
    public BigDecimal getCategoryAvgRating(String category) {
        // 此方法预留扩展，后续通过 JPA Native Query 实现
        // 当前返回 null 表示无对比数据
        return null;
    }
}
