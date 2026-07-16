package com.example.demo.service;

import com.example.demo.common.CacheNames;
import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.CheckInRepository;
import com.example.demo.repository.FeedbackRepository;
import com.example.demo.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

        long approvedCount = registrationRepository.countByActivityIdAndStatus(
                activityId, "approved");

        long checkInCount = checkInRepository.countByActivityId(activityId);
        Map<String, Long> checkInMethodsStats = computeCheckInMethodStats(activityId);

        var feedbackList = feedbackRepository.findByActivityIdOrderByCreatedAtDesc(activityId);
        long feedbackCount = feedbackList.size();
        BigDecimal avgRating = computeAvgRating(activityId);
        Map<Integer, Long> ratingDistribution = computeRatingDistribution(activityId);

        List<String> feedbackContents = feedbackList.stream()
                .map(f -> f.getRating() + "星：" + sanitizeFeedback(f.getContent()))
                .toList();

        long signupCount = activity.getSignupCount() == null ? 0 : activity.getSignupCount();
        BigDecimal signupRate = calcRate(signupCount,
                activity.getViewCount() == null ? 0 : activity.getViewCount());

        BigDecimal attendanceRate = calcRate(checkInCount, signupCount);

        return ActivityMetrics.builder()
                .activityId(activity.getId())
                .activityTitle(activity.getTitle())
                .category(activity.getCategory())
                .location(activity.getLocation())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())

                .viewCount(activity.getViewCount())
                .signupCount(activity.getSignupCount())
                .maxParticipants(activity.getMaxParticipants())
                .signupRate(signupRate)
                .approvedCount(approvedCount)
                .checkInCount(checkInCount)
                .attendanceRate(attendanceRate)
                .favoriteCount(activity.getFavoriteCount())

                .feedbackCount(feedbackCount)
                .avgRating(avgRating)
                .ratingDistribution(ratingDistribution)

                .checkInMethodsStats(checkInMethodsStats)
                .feedbackContents(feedbackContents)
                .signupTrend(computeSignupTrend(activityId, activity))
                .build();
    }

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

    private Map<Integer, Long> computeRatingDistribution(Long activityId) {
        var feedbacks = feedbackRepository.findByActivityIdOrderByCreatedAtDesc(activityId);
        Map<Integer, Long> distribution = new LinkedHashMap<>();

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
     * 计算活动报名趋势，按天聚合。
     * <p>
     * 区间口径：
     * <ul>
     *   <li>下界 = {@code min(最早报名日, 活动开始日)} —— 报名往往发生在活动开始前</li>
     *   <li>上界 = {@code 活动开始日} —— 报名截止于活动开始；{@code activity_end} 仅作兜底</li>
     * </ul>
     * 用 {@link TreeMap}（{@code LocalDate} key）保证补零区间按日期合并、
     * 数据库返回行按日期追加时不会乱序；最后再格式化成 {@code MM-dd} 字符串返回。
     */
    private Map<String, Long> computeSignupTrend(Long activityId, Activity activity) {
        List<Object[]> rows = registrationRepository.countDailySignupsByActivityId(activityId);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        TreeMap<LocalDate, Long> series = new TreeMap<>();

        LocalDate activityStart = activity.getStartTime() != null
                ? activity.getStartTime().toLocalDate() : null;
        LocalDate activityEnd = activity.getEndTime() != null
                ? activity.getEndTime().toLocalDate() : null;

        // 先把真实报名数据按 LocalDate 装进 TreeMap，自然按日期排序
        for (Object[] row : rows) {
            if (row[0] != null) {
                LocalDate day = toLocalDate(row[0]);
                Long count = ((Number) row[1]).longValue();
                series.merge(day, count, Long::sum);
            }
        }

        // 下界：最早报名日 与 活动开始日 中更早的那个
        // 上界：活动开始日（报名截止日）；若活动已开始则取活动结束日兜底
        LocalDate earliestSignup = series.isEmpty() ? null : series.firstKey();
        LocalDate lower = pickEarlier(earliestSignup, activityStart);
        LocalDate upper;
        if (activityStart != null) {
            // 报名截止于活动开始；如果活动已经开始/结束，则补到活动结束日
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
            // 在 [lower, upper] 区间内补零，TreeMap.merge 不会覆盖已有真实数据
            for (LocalDate d = lower; !d.isAfter(upper); d = d.plusDays(1)) {
                series.putIfAbsent(d, 0L);
            }
        }

        // 转成对外约定的 LinkedHashMap<String, Long>，按日期升序
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

    /**
     * 把 {@code DATE(created_at)} 的 JDBC 返回值统一转成 {@link LocalDate}。
     * <p>
     * 不同驱动 / Hibernate 版本下返回值类型可能不同：
     * <ul>
     *   <li>老版 MySQL Connector/J：{@link java.sql.Date}</li>
     *   <li>新版 Hibernate 6+/7+：{@link LocalDate}</li>
     * </ul>
     * 直接强转会在其中一种环境下抛 {@link ClassCastException}，
     * 这里做一次类型兼容以避免在两种环境下都需要修改业务代码。
     *
     * @param raw JDBC 返回的第一个字段
     * @return 解析出的日期
     */
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

    @Transactional(readOnly = true)
    public BigDecimal getCategoryAvgAttendanceRate(String category) {
        return null;
    }

    @Transactional(readOnly = true)
    public BigDecimal getCategoryAvgRating(String category) {
        return null;
    }
}
