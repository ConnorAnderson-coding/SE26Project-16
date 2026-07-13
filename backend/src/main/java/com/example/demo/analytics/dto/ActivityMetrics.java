package com.example.demo.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 活动指标数据传输对象
 * <p>
 * 包含单个活动的全部核心分析指标，由 {@code AnalyticsEngine} 计算生成。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityMetrics {

    // ──────────── 活动基本信息 ────────────

    /** 活动ID */
    private Long activityId;

    /** 活动名称 */
    private String activityTitle;

    /** 活动类别 */
    private String category;

    /** 活动地点 */
    private String location;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    // ──────────── 核心指标 ────────────

    /** 浏览量 */
    private Integer viewCount;

    /** 报名人数 */
    private Integer signupCount;

    /** 人数上限 */
    private Integer maxParticipants;

    /** 报名转化率 (%) — signupCount / viewCount */
    private BigDecimal signupRate;

    /** 审核通过人数 */
    private Long approvedCount;

    /** 签到人数 */
    private Long checkInCount;

    /** 到场率 (%) — checkInCount / signupCount */
    private BigDecimal attendanceRate;

    /** 收藏数（辅助维度） */
    private Integer favoriteCount;

    // ──────────── 评分指标 ────────────

    /** 评价总数 */
    private Long feedbackCount;

    /** 平均评分 (1.00-5.00)，无评价时为 null */
    private BigDecimal avgRating;

    /** 评分分布: key=星级(1-5), value=数量 */
    private Map<Integer, Long> ratingDistribution;

    // ──────────── 签到方式统计 ────────────

    /** 签到方式分布: key=签到方式, value=人数 */
    private Map<String, Long> checkInMethodsStats;

    // ──────────── 用户评价内容（已脱敏） ────────────

    /** 用户文字评价列表（仅含评分+内容，已剥离用户身份信息） */
    private List<String> feedbackContents;
}
