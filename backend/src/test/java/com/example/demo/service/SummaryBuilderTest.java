package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定 {@link SummaryBuilder} 输出给 LLM 的"指标—文字"一致性，
 * 防止分母再次写错（典型回归：把容量当成浏览量）。
 */
class SummaryBuilderTest {

    private final SummaryBuilder summaryBuilder = new SummaryBuilder();

    @Test
    void conversionRateDenominatorMustBeViewCount() {
        ActivityMetrics metrics = baseMetrics()
                .viewCount(20)
                .signupCount(7)
                .maxParticipants(50)
                .signupRate(new BigDecimal("35.0"))
                .build();

        String summary = summaryBuilder.build(metrics);

        // 报名转化率括号里的分母必须与 signupRate 计算口径一致（即 viewCount）
        assertTrue(summary.contains("报名转化率：35.0%（报名人数：7/20 次浏览）"),
                "报名转化率应使用 viewCount 作分母，实际摘要：" + summary);
    }

    @Test
    void capacityUtilizationIsReportedSeparately() {
        ActivityMetrics metrics = baseMetrics()
                .viewCount(20)
                .signupCount(7)
                .maxParticipants(50)
                .signupRate(new BigDecimal("35.0"))
                .build();

        String summary = summaryBuilder.build(metrics);

        // 必须独立输出一行"容量使用率 = 7 / 50 = 14%"
        assertTrue(summary.contains("容量使用率：14.0%（报名人数：7/50 容量）"),
                "应独立输出容量使用率行，实际摘要：" + summary);
    }

    @Test
    void summaryDoesNotMixMaxParticipantsIntoConversionRateLine() {
        ActivityMetrics metrics = baseMetrics()
                .viewCount(20)
                .signupCount(7)
                .maxParticipants(50)
                .signupRate(new BigDecimal("35.0"))
                .build();

        String summary = summaryBuilder.build(metrics);

        // 防御性断言：报名转化率那一行不能再出现 maxParticipants（之前 bug 的根因）
        String conversionLine = summary.lines()
                .filter(l -> l.contains("报名转化率"))
                .findFirst()
                .orElse("");
        assertFalse(conversionLine.contains("/50"),
                "报名转化率行的分母不应再包含容量上限 50；实际：" + conversionLine);
        assertFalse(conversionLine.contains("maxParticipants"));
    }

    @Test
    void capacityUtilizationIsSkippedWhenMaxParticipantsMissing() {
        ActivityMetrics metrics = baseMetrics()
                .viewCount(20)
                .signupCount(7)
                .maxParticipants(null)
                .signupRate(new BigDecimal("35.0"))
                .build();

        String summary = summaryBuilder.build(metrics);

        assertFalse(summary.contains("容量使用率"),
                "未设置容量上限时不应输出容量使用率行：" + summary);
    }

    private static ActivityMetrics.ActivityMetricsBuilder baseMetrics() {
        return ActivityMetrics.builder()
                .activityId(1L)
                .activityTitle("智能分析演示：校园 AI 实践工作坊")
                .category("academic")
                .location("软件学院 报告厅")
                .startTime(LocalDateTime.of(2026, 7, 10, 19, 0))
                .endTime(LocalDateTime.of(2026, 7, 10, 21, 0))
                .approvedCount(5L)
                .checkInCount(4L)
                .attendanceRate(new BigDecimal("80.0"))
                .favoriteCount(3)
                .feedbackCount(0L)
                .ratingDistribution(Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L));
    }
}
