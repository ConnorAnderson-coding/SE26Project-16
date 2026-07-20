package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.entity.Activity;
import com.example.demo.entity.Feedback;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.CheckInRepository;
import com.example.demo.repository.FeedbackRepository;
import com.example.demo.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsEngineTest {

    @Mock ActivityRepository activityRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock CheckInRepository checkInRepository;
    @Mock FeedbackRepository feedbackRepository;
    @InjectMocks AnalyticsEngine analyticsEngine;

    private Activity activity;

    @BeforeEach
    void setUp() {
        activity = new Activity();
        activity.setId(7L);
        activity.setTitle("校园羽毛球友谊赛");
        activity.setCategory("sports");
        activity.setLocation("体育馆");
        activity.setViewCount(100);
        activity.setFavoriteCount(10);
        activity.setMaxParticipants(50);
        activity.setCreatedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        activity.setStartTime(LocalDateTime.of(2026, 7, 10, 9, 0));
        activity.setEndTime(LocalDateTime.of(2026, 7, 10, 12, 0));
    }

    private void stubBaseCounts() {
        when(activityRepository.findById(7L)).thenReturn(Optional.of(activity));
        when(registrationRepository.countByActivityId(7L)).thenReturn(40L);
        when(registrationRepository.countByActivityIdAndStatus(7L, "approved")).thenReturn(30L);
        when(registrationRepository.countDailySignupsByActivityId(7L)).thenReturn(List.of());
        when(checkInRepository.countByActivityId(7L)).thenReturn(24L);
        when(checkInRepository.countByMethodGroupByActivityId(7L)).thenReturn(List.of(
                new Object[]{"qrcode", 15L},
                new Object[]{"location", 9L}
        ));
        when(feedbackRepository.findByActivityIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
    }

    @Test
    void metricsUseActivityAggregateColumnsLikeDetailPage() {
        stubBaseCounts();
        ActivityMetrics metrics = analyticsEngine.computeMetrics(7L);

        assertThat(metrics.getSignupCount()).isEqualTo(40);
        assertThat(metrics.getViewCount()).isEqualTo(100);
        assertThat(metrics.getFavoriteCount()).isEqualTo(10);
        assertThat(metrics.getApprovedCount()).isEqualTo(30L);
        assertThat(metrics.getCheckInCount()).isEqualTo(24L);
        // 报名转化率 = 40/100 * 100
        assertThat(metrics.getSignupRate()).isEqualByComparingTo("40.0");
        // 到场率 = 24/40 * 100
        assertThat(metrics.getAttendanceRate()).isEqualByComparingTo("60.0");
        assertThat(metrics.getCheckInMethodsStats())
                .containsEntry("qrcode", 15L)
                .containsEntry("location", 9L);
    }

    @Test
    void signupRateIsZeroWhenViewCountIsZero() {
        activity.setViewCount(0);
        stubBaseCounts();
        when(registrationRepository.countByActivityId(7L)).thenReturn(10L);
        when(checkInRepository.countByActivityId(7L)).thenReturn(0L);

        ActivityMetrics metrics = analyticsEngine.computeMetrics(7L);

        assertThat(metrics.getSignupRate()).isEqualByComparingTo("0.0");
        assertThat(metrics.getViewCount()).isEqualTo(0);
        assertThat(metrics.getSignupCount()).isEqualTo(10);
    }

    @Test
    void attendanceRateIsZeroWhenSignupCountIsZero() {
        activity.setViewCount(50);
        stubBaseCounts();
        when(registrationRepository.countByActivityId(7L)).thenReturn(0L);
        when(checkInRepository.countByActivityId(7L)).thenReturn(5L); // 异常：签到 > 报名

        ActivityMetrics metrics = analyticsEngine.computeMetrics(7L);

        assertThat(metrics.getAttendanceRate()).isEqualByComparingTo("0.0");
        assertThat(metrics.getCheckInCount()).isEqualTo(5L);
    }

    @Test
    void nullAggregateCountsTreatedAsZero() {
        activity.setViewCount(null);
        activity.setFavoriteCount(null);
        stubBaseCounts();
        when(registrationRepository.countByActivityId(7L)).thenReturn(0L);

        ActivityMetrics metrics = analyticsEngine.computeMetrics(7L);

        assertThat(metrics.getViewCount()).isEqualTo(0);
        assertThat(metrics.getSignupCount()).isEqualTo(0);
        assertThat(metrics.getFavoriteCount()).isEqualTo(0);
        assertThat(metrics.getSignupRate()).isEqualByComparingTo("0.0");
        assertThat(metrics.getAttendanceRate()).isEqualByComparingTo("0.0");
    }

    @Test
    void avgRatingAndDistributionWithFeedback() {
        stubBaseCounts();
        Feedback f1 = feedback(5, "很好");
        Feedback f2 = feedback(4, "不错");
        Feedback f3 = feedback(4, "还可以");
        when(feedbackRepository.findByActivityIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(f1, f2, f3));

        ActivityMetrics metrics = analyticsEngine.computeMetrics(7L);

        assertThat(metrics.getFeedbackCount()).isEqualTo(3L);
        // (5+4+4)/3 = 4.333... → 4.33
        assertThat(metrics.getAvgRating()).isEqualByComparingTo("4.33");
        assertThat(metrics.getRatingDistribution())
                .containsEntry(1, 0L)
                .containsEntry(2, 0L)
                .containsEntry(3, 0L)
                .containsEntry(4, 2L)
                .containsEntry(5, 1L);
        assertThat(metrics.getFeedbackContents())
                .hasSize(3)
                .allMatch(s -> s.contains("星："));
    }

    @Test
    void avgRatingIsNullAndDistributionAllZeroWhenNoFeedback() {
        stubBaseCounts();
        when(feedbackRepository.findByActivityIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());

        ActivityMetrics metrics = analyticsEngine.computeMetrics(7L);

        assertThat(metrics.getFeedbackCount()).isEqualTo(0L);
        assertThat(metrics.getAvgRating()).isNull();
        assertThat(metrics.getRatingDistribution().values()).containsOnly(0L);
        assertThat(metrics.getFeedbackContents()).isEmpty();
    }

    @Test
    void sanitizeFeedbackInContents() {
        stubBaseCounts();
        Feedback f = feedback(5,
                "联系我：张三 13812345678 邮箱 test.user@sjtu.edu.cn 学号 524030910001");
        when(feedbackRepository.findByActivityIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(f));

        ActivityMetrics metrics = analyticsEngine.computeMetrics(7L);

        String content = metrics.getFeedbackContents().get(0);
        assertThat(content).doesNotContain("13812345678");
        assertThat(content).doesNotContain("test.user@sjtu.edu.cn");
        assertThat(content).doesNotContain("524030910001");
        assertThat(content).contains("[手机号已脱敏]");
        assertThat(content).contains("[邮箱已脱敏]");
        assertThat(content).contains("[编号已脱敏]");
        // 姓名当前实现不脱敏；语义仍可读
        assertThat(content).contains("联系我");
    }

    @Test
    void throwsWhenActivityMissing() {
        when(activityRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> analyticsEngine.computeMetrics(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("活动不存在");
    }

    // ---------------- sanitizeFeedback 纯函数边界 ----------------

    @Test
    void sanitizeFeedbackHandlesNullBlankAndMixedPii() {
        assertThat(AnalyticsEngine.sanitizeFeedback(null)).isEmpty();
        assertThat(AnalyticsEngine.sanitizeFeedback("   ")).isEmpty();
        assertThat(AnalyticsEngine.sanitizeFeedback("场地很好")).isEqualTo("场地很好");

        String mixed = "请联系 Alice 手机 13900001111 或 admin@example.com，工号 2024012345";
        String out = AnalyticsEngine.sanitizeFeedback(mixed);
        assertThat(out).contains("[手机号已脱敏]");
        assertThat(out).contains("[邮箱已脱敏]");
        assertThat(out).contains("[编号已脱敏]");
        assertThat(out).contains("请联系");
        assertThat(out).doesNotContain("13900001111");
        assertThat(out).doesNotContain("admin@example.com");
    }

    // ---------------- 报名趋势边界 ----------------

    /** 报名期：createdAt(07-01) ~ startTime(07-10)，签到数据落在 [07-02, 07-05] 应保留 */
    @Test
    void signupTrendBoundsToRegistrationWindow() {
        when(activityRepository.findById(7L)).thenReturn(Optional.of(activity));
        when(registrationRepository.countByActivityIdAndStatus(7L, "approved")).thenReturn(0L);
        when(registrationRepository.countDailySignupsByActivityId(7L)).thenReturn(List.of(
                new Object[]{Date.valueOf("2026-07-02"), 3L},
                new Object[]{Date.valueOf("2026-07-05"), 5L}
        ));
        when(checkInRepository.countByActivityId(7L)).thenReturn(0L);
        when(checkInRepository.countByMethodGroupByActivityId(7L)).thenReturn(List.of());
        when(feedbackRepository.findByActivityIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());

        ActivityMetrics metrics = analyticsEngine.computeMetrics(7L);

        assertThat(metrics.getSignupTrend())
                .containsKeys("07-01", "07-02", "07-03", "07-04", "07-05",
                        "07-06", "07-07", "07-08", "07-09", "07-10")
                .containsEntry("07-02", 3L)
                .containsEntry("07-05", 5L)
                .containsEntry("07-03", 0L); // 中间无数据补零
    }

    /** 报名期外的脏数据应被剔除（不应出现在趋势中） */
    @Test
    void signupTrendDropsDataOutsideRegistrationWindow() {
        when(activityRepository.findById(7L)).thenReturn(Optional.of(activity));
        when(registrationRepository.countByActivityIdAndStatus(7L, "approved")).thenReturn(0L);
        // 06-25 在 createdAt(07-01) 之前；07-15 在 startTime(07-10) 之后
        when(registrationRepository.countDailySignupsByActivityId(7L)).thenReturn(List.of(
                new Object[]{Date.valueOf("2026-06-25"), 99L},
                new Object[]{Date.valueOf("2026-07-03"), 4L},
                new Object[]{Date.valueOf("2026-07-15"), 88L}
        ));
        when(checkInRepository.countByActivityId(7L)).thenReturn(0L);
        when(checkInRepository.countByMethodGroupByActivityId(7L)).thenReturn(List.of());
        when(feedbackRepository.findByActivityIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());

        ActivityMetrics metrics = analyticsEngine.computeMetrics(7L);

        assertThat(metrics.getSignupTrend())
                .doesNotContainKey("06-25")
                .doesNotContainKey("07-15")
                .containsEntry("07-03", 4L);
    }

    private static Feedback feedback(int rating, String content) {
        Feedback f = new Feedback();
        f.setRating(rating);
        f.setContent(content);
        return f;
    }
}
