package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryBuilderTest {

    private final SummaryBuilder summaryBuilder = new SummaryBuilder();

    @Test
    void buildIncludesBasicInfoCoreMetricsAndFeedback() {
        Map<Integer, Long> dist = new LinkedHashMap<>();
        dist.put(5, 2L);
        dist.put(4, 1L);
        dist.put(3, 0L);
        dist.put(2, 0L);
        dist.put(1, 0L);

        ActivityMetrics metrics = ActivityMetrics.builder()
                .activityTitle("校园羽毛球友谊赛")
                .category("sports")
                .location("体育馆")
                .startTime(LocalDateTime.of(2026, 7, 10, 9, 0))
                .endTime(LocalDateTime.of(2026, 7, 10, 12, 0))
                .viewCount(100)
                .signupCount(40)
                .maxParticipants(50)
                .signupRate(new BigDecimal("40.0"))
                .approvedCount(30L)
                .checkInCount(24L)
                .attendanceRate(new BigDecimal("60.0"))
                .feedbackCount(3L)
                .avgRating(new BigDecimal("4.33"))
                .ratingDistribution(dist)
                .feedbackContents(List.of("5星：很好", "4星：不错"))
                .checkInMethodsStats(Map.of("qrcode", 15L, "password", 9L))
                .build();

        String summary = summaryBuilder.build(metrics);

        assertThat(summary)
                .contains("## 活动基本信息")
                .contains("校园羽毛球友谊赛")
                .contains("体育运动")
                .contains("体育馆")
                .contains("## 核心指标")
                .contains("浏览量：100 次")
                .contains("报名转化率：40.0%")
                .contains("容量使用率：80.0%")
                .contains("已通过人数：30 人")
                .contains("到场率：60.0%")
                .contains("## 评价数据")
                .contains("平均评分：4.33 / 5.00")
                .contains("5星：2")
                .contains("## 文字反馈摘要（已脱敏）")
                .contains("很好")
                .contains("## 签到方式统计")
                .contains("二维码签到：15 人")
                .contains("签到码签到：9 人");
    }

    @Test
    void buildHandlesEmptyFeedbackAndNullRates() {
        ActivityMetrics metrics = ActivityMetrics.builder()
                .activityTitle("空数据活动")
                .category("unknown-cat")
                .location(null)
                .viewCount(0)
                .signupCount(0)
                .maxParticipants(0)
                .signupRate(BigDecimal.ZERO)
                .attendanceRate(BigDecimal.ZERO)
                .approvedCount(0L)
                .checkInCount(0L)
                .feedbackCount(0L)
                .avgRating(null)
                .feedbackContents(List.of())
                .build();

        String summary = summaryBuilder.build(metrics);

        assertThat(summary)
                .contains("空数据活动")
                .contains("unknown-cat") // 未知类别原样输出
                .contains("暂无评价")
                .doesNotContain("文字反馈摘要")
                .doesNotContain("容量使用率"); // maxParticipants<=0 不输出
    }

    @Test
    void buildStillReadableWithExtremeRates() {
        ActivityMetrics metrics = ActivityMetrics.builder()
                .activityTitle("极端数据")
                .category("academic")
                .location("A101")
                .viewCount(2)
                .signupCount(22)
                .maxParticipants(40)
                .signupRate(new BigDecimal("1100.0"))
                .approvedCount(1L)
                .checkInCount(0L)
                .attendanceRate(BigDecimal.ZERO)
                .feedbackCount(0L)
                .build();

        String summary = summaryBuilder.build(metrics);

        assertThat(summary)
                .contains("学术讲座")
                .contains("报名转化率：1100.0%")
                .contains("到场率：0%");
    }
}
