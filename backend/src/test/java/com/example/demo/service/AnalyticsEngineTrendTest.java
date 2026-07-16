package com.example.demo.service;

import com.example.demo.entity.Activity;
import com.example.demo.entity.Feedback;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.CheckInRepository;
import com.example.demo.repository.FeedbackRepository;
import com.example.demo.repository.RegistrationRepository;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 锁定 {@link AnalyticsEngine#computeMetrics(Long)} 输出的报名趋势区间：
 * <ul>
 *   <li>下界 = {@code min(最早报名日, 活动开始日)}</li>
 *   <li>上界 = {@code 活动开始日}（报名截止），活动开始后兜底到活动结束日</li>
 *   <li>区间内缺失的天补 0</li>
 * </ul>
 * <p>
 * 用 {@link java.sql.Date} 模拟老版 MySQL 驱动的返回类型，
 * 顺带验证 {@code toLocalDate()} 的类型兼容。
 */
class AnalyticsEngineTrendTest {

    @Test
    void signupTrendRangeIsClippedToActivityStart() {
        RegistrationRepository regRepo = mock(RegistrationRepository.class);
        when(regRepo.countByActivityIdAndStatus(anyLong(), eq("approved"))).thenReturn(0L);
        // 报名发生在活动开始之前 + 当天；最早 07-01，最晚 07-10（活动开始日）
        List<Object[]> rows = List.<Object[]>of(
                new Object[]{Date.valueOf("2026-07-01"), 1L},
                new Object[]{Date.valueOf("2026-07-05"), 2L},
                new Object[]{Date.valueOf("2026-07-10"), 3L}
        );
        when(regRepo.countDailySignupsByActivityId(anyLong())).thenReturn(rows);

        AnalyticsEngine engine = newEngine(regRepo);

        Map<String, Long> trend = engine.computeMetrics(1L).getSignupTrend();

        assertNotNull(trend);
        // 区间应为 07-01 ~ 07-10，共 10 天；不应出现 07-11 之后的数据
        assertTrue(trend.containsKey("07-01"), "趋势应包含最早报名日");
        assertTrue(trend.containsKey("07-10"), "趋势应包含活动开始日");
        assertTrue(trend.size() <= 10, "趋势区间不应超过活动开始日：" + trend.keySet());
        // 中间缺的天补 0
        assertEquals(0L, trend.get("07-03"));
        // 真实报名数
        assertEquals(1L, trend.get("07-01"));
        assertEquals(2L, trend.get("07-05"));
        assertEquals(3L, trend.get("07-10"));
    }

    @Test
    void signupTrendRangeIncludesSignupWindowBeforeActivityStart() {
        RegistrationRepository regRepo = mock(RegistrationRepository.class);
        when(regRepo.countByActivityIdAndStatus(anyLong(), eq("approved"))).thenReturn(0L);
        // 报名仅发生在 07-02，早于活动开始
        List<Object[]> rows = List.<Object[]>of(
                new Object[]{Date.valueOf("2026-07-02"), 5L}
        );
        when(regRepo.countDailySignupsByActivityId(anyLong())).thenReturn(rows);

        AnalyticsEngine engine = newEngine(regRepo);

        Map<String, Long> trend = engine.computeMetrics(1L).getSignupTrend();

        // 区间必须把 07-02 纳入，不能被活动开始日"截断"成 07-10 之后才开始
        assertTrue(trend.containsKey("07-02"), "报名日的窗口必须包含 07-02：" + trend.keySet());
        assertTrue(trend.containsKey("07-10"), "趋势必须延伸到活动开始日");
        assertEquals(5L, trend.get("07-02"));
    }

    @Test
    void signupTrendFillsZeroForMissingDays() {
        RegistrationRepository regRepo = mock(RegistrationRepository.class);
        when(regRepo.countByActivityIdAndStatus(anyLong(), eq("approved"))).thenReturn(0L);
        List<Object[]> rows = List.<Object[]>of(
                new Object[]{Date.valueOf("2026-07-10"), 4L}
        );
        when(regRepo.countDailySignupsByActivityId(anyLong())).thenReturn(rows);

        AnalyticsEngine engine = newEngine(regRepo);

        Map<String, Long> trend = engine.computeMetrics(1L).getSignupTrend();

        // 当日活动，无前置报名：区间应该只有 07-10 一天，且值为 4
        assertEquals(1, trend.size(), "单日活动应只输出一天数据：" + trend);
        assertEquals(4L, trend.get("07-10"));
    }

    @Test
    void signupTrendRowsFromDatabaseAreSortedByDate() {
        RegistrationRepository regRepo = mock(RegistrationRepository.class);
        when(regRepo.countByActivityIdAndStatus(anyLong(), eq("approved"))).thenReturn(0L);
        // 数据库返回顺序无所谓：TreeMap 会按 LocalDate 自然排序
        List<Object[]> rows = List.<Object[]>of(
                new Object[]{Date.valueOf("2026-07-10"), 3L},
                new Object[]{Date.valueOf("2026-07-05"), 2L},
                new Object[]{Date.valueOf("2026-07-08"), 1L},
                new Object[]{Date.valueOf("2026-07-01"), 4L}
        );
        when(regRepo.countDailySignupsByActivityId(anyLong())).thenReturn(rows);

        AnalyticsEngine engine = newEngine(regRepo);

        Map<String, Long> trend = engine.computeMetrics(1L).getSignupTrend();

        // 验证 LinkedHashMap 按日期升序
        String[] ordered = trend.keySet().toArray(new String[0]);
        for (int i = 1; i < ordered.length; i++) {
            assertTrue(LocalDate.parse("2026-" + ordered[i])
                            .isAfter(LocalDate.parse("2026-" + ordered[i - 1])),
                    "趋势日期必须升序：" + Arrays.toString(ordered));
        }
    }

    /* ==================== helpers ==================== */

    private static Activity activity(LocalDateTime start, LocalDateTime end) {
        Activity a = new Activity();
        a.setId(1L);
        a.setTitle("测试活动");
        a.setCategory("academic");
        a.setDescription("trend range test");
        a.setStartTime(start);
        a.setEndTime(end);
        a.setLocation("软件学院");
        a.setOrganizerId("u-test");
        a.setCollege("软件学院");
        a.setMaxParticipants(50);
        a.setSignupCount(0);
        a.setViewCount(0);
        a.setFavoriteCount(0);
        return a;
    }

    /**
     * 反射注入字段，因为 {@link AnalyticsEngine} 用 {@code @RequiredArgsConstructor}
     * + {@code final} 字段，构造器需要全部 repository。
     * 这样写比 Mockito 的 {@code @InjectMocks} 更直接，依赖更少。
     */
    private static AnalyticsEngine newEngine(RegistrationRepository regRepo) {
        ActivityRepository activityRepo = mock(ActivityRepository.class);
        when(activityRepo.findById(anyLong())).thenReturn(java.util.Optional.of(
                activity(LocalDateTime.of(2026, 7, 10, 19, 0),
                         LocalDateTime.of(2026, 7, 10, 21, 0))));

        CheckInRepository checkInRepo = mock(CheckInRepository.class);
        when(checkInRepo.countByActivityId(anyLong())).thenReturn(0L);
        when(checkInRepo.countByMethodGroupByActivityId(anyLong())).thenReturn(List.of());

        FeedbackRepository feedbackRepo = mock(FeedbackRepository.class);
        when(feedbackRepo.findByActivityIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.<Feedback>of());

        ActivityAnalysisRepository analysisRepo = mock(ActivityAnalysisRepository.class);
        when(analysisRepo.findByActivityId(anyLong())).thenReturn(java.util.Optional.empty());

        return new AnalyticsEngine(activityRepo, regRepo, checkInRepo, feedbackRepo, analysisRepo);
    }
}
