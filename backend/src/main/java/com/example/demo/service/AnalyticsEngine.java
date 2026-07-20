package com.example.demo.service;

import com.example.demo.common.CacheNames;
import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.CheckInRepository;
import com.example.demo.repository.FeedbackRepository;
import com.example.demo.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 活动分析指标计算。
 * <p>
 * 报名/浏览/收藏等展示计数与活动详情同源（activity 表冗余字段），
 * 避免分析页与详情页数字不一致；签到、评价、趋势等从明细表实时统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsEngine {

    private final ActivityRepository activityRepository;
    private final RegistrationRepository registrationRepository;
    private final CheckInRepository checkInRepository;
    private final FeedbackRepository feedbackRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.ANALYTICS_ACTIVITY, key = "#activityId",
            unless = "#result == null")
    public ActivityMetrics computeMetrics(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("活动不存在: " + activityId));

        // 与详情页一致：使用 activity 表上的冗余计数
        int viewCount = nullToZero(activity.getViewCount());
        int signupCount = nullToZero(activity.getSignupCount());
        int favoriteCount = nullToZero(activity.getFavoriteCount());

        long approvedCount = registrationRepository.countByActivityIdAndStatus(
                activityId, "approved");

        long checkInCount = checkInRepository.countByActivityId(activityId);
        Map<String, Long> checkInMethodsStats = computeCheckInMethodStats(activityId);

        var feedbackList = feedbackRepository.findByActivityIdOrderByCreatedAtDesc(activityId);
        long feedbackCount = feedbackList.size();
        BigDecimal avgRating = computeAvgRating(feedbackList);
        Map<Integer, Long> ratingDistribution = computeRatingDistribution(feedbackList);

        List<String> feedbackContents = feedbackList.stream()
                .map(f -> f.getRating() + "星：" + sanitizeFeedback(f.getContent()))
                .toList();

        // 报名转化率 = 报名 / 浏览；到场率 = 签到 / 报名（与展示的报名人数同一分母）
        BigDecimal signupRate = calcRate(signupCount, viewCount);
        BigDecimal attendanceRate = calcRate(checkInCount, signupCount);

        return ActivityMetrics.builder()
                .activityId(activity.getId())
                .activityTitle(activity.getTitle())
                .category(activity.getCategory())
                .location(activity.getLocation())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .viewCount(viewCount)
                .signupCount(signupCount)
                .maxParticipants(activity.getMaxParticipants())
                .signupRate(signupRate)
                .approvedCount(approvedCount)
                .checkInCount(checkInCount)
                .attendanceRate(attendanceRate)
                .favoriteCount(favoriteCount)
                .snapshotAt(LocalDateTime.now())
                .feedbackCount(feedbackCount)
                .avgRating(avgRating)
                .ratingDistribution(ratingDistribution)
                .checkInMethodsStats(checkInMethodsStats)
                .feedbackContents(feedbackContents)
                .signupTrend(computeSignupTrend(activityId, activity))
                .build();
    }

    private static int nullToZero(Integer v) {
        return v == null ? 0 : v;
    }

    private BigDecimal computeAvgRating(List<com.example.demo.entity.Feedback> feedbacks) {
        if (feedbacks.isEmpty()) {
            return null;
        }
        double avg = feedbacks.stream()
                .mapToInt(com.example.demo.entity.Feedback::getRating)
                .average()
                .orElse(0);
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<Integer, Long> computeRatingDistribution(
            List<com.example.demo.entity.Feedback> feedbacks) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        feedbacks.forEach(f -> distribution.merge(f.getRating(), 1L, Long::sum));
        return distribution;
    }

    private Map<String, Long> computeSignupTrend(Long activityId, Activity activity) {
        List<Object[]> rows = registrationRepository.countDailySignupsByActivityId(activityId);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        TreeMap<LocalDate, Long> series = new TreeMap<>();

        LocalDate activityStart = activity.getStartTime() != null
                ? activity.getStartTime().toLocalDate() : null;
        LocalDate activityEnd = activity.getEndTime() != null
                ? activity.getEndTime().toLocalDate() : null;

        for (Object[] row : rows) {
            if (row[0] != null) {
                LocalDate day = toLocalDate(row[0]);
                Long count = ((Number) row[1]).longValue();
                series.merge(day, count, Long::sum);
            }
        }

        LocalDate earliestSignup = series.isEmpty() ? null : series.firstKey();
        LocalDate lower = pickEarlier(earliestSignup, activityStart);
        LocalDate upper;
        if (activityStart != null) {
            LocalDate today = LocalDate.now();
            if (activityStart.isAfter(today)) {
                upper = activityStart;
            } else {
                upper = activityEnd != null && activityEnd.isAfter(today) ? today : activityEnd;
            }
        } else {
            upper = activityEnd;
        }

        if (lower != null && upper != null && !lower.isAfter(upper)) {
            for (LocalDate d = lower; !d.isAfter(upper); d = d.plusDays(1)) {
                series.putIfAbsent(d, 0L);
            }
        }

        Map<String, Long> trend = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, Long> e : series.entrySet()) {
            trend.put(e.getKey().format(formatter), e.getValue());
        }
        return trend;
    }

    private static LocalDate pickEarlier(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate toLocalDate(Object raw) {
        if (raw instanceof LocalDate ld) {
            return ld;
        }
        if (raw instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (raw instanceof java.util.Date utilDate) {
            return new java.sql.Date(utilDate.getTime()).toLocalDate();
        }
        throw new IllegalArgumentException(
                "无法识别的日期类型: " + (raw == null ? "null" : raw.getClass().getName()));
    }

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

    private BigDecimal calcRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    static String sanitizeFeedback(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content
                .replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[邮箱已脱敏]")
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "[手机号已脱敏]")
                .replaceAll("(?<!\\d)\\d{8,18}(?!\\d)", "[编号已脱敏]");
    }
}
