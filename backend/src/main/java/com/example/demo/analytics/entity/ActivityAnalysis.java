package com.example.demo.analytics.entity;

import com.example.demo.entity.Activity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 活动分析结果实体
 * <p>
 * 每个活动最多一条分析记录，由定时任务或手动触发生成。
 * 包含核心指标、评分分布、签到统计、LLM建议等完整分析数据。
 *
 * @see Activity 关联活动
 */
@Getter
@Setter
@Entity
@Table(name = "activity_analysis")
public class ActivityAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false,
            insertable = false, updatable = false)
    private Long activityId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    // ──────────── 核心指标 ────────────

    /** 报名转化率 (%) */
    @Column(name = "signup_rate", nullable = false, precision = 5, scale = 1)
    private BigDecimal signupRate;

    /** 到场率 (%) */
    @Column(name = "attendance_rate", nullable = false, precision = 5, scale = 1)
    private BigDecimal attendanceRate;

    /** 平均评分 (1.00-5.00) */
    @Column(name = "avg_rating", precision = 3, scale = 2)
    private BigDecimal avgRating;

    /** 收藏转化率 (%) */
    @Column(name = "favorite_conversion", precision = 5, scale = 1)
    private BigDecimal favoriteConversion;

    // ──────────── 指标明细 (JSON) ────────────

    /** 评分分布: {"1": count, "2": count, ..., "5": count} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rating_distribution", columnDefinition = "json")
    private Map<Integer, Long> ratingDistribution;

    /** 签到方式统计: {"qrcode": count, "location": count, "password": count} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "check_in_methods_stats", columnDefinition = "json")
    private Map<String, Long> checkInMethodsStats;

    /** 完整指标快照，含辅助维度对比数据 (同类活动历史均值等) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json", columnDefinition = "json")
    private Map<String, Object> metricsJson;

    // ──────────── LLM 建议 ────────────

    /**
     * 改进建议列表
     * <p>
     * JSON 结构示例:
     * <pre>{@code
     * [
     *   {
     *     "id": "id-1",
     *     "category": "promotion",
     *     "priority": "high",
     *     "content": "建议在学院公众号增加活动推送..."
     *   }
     * ]
     * }</pre>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestions", columnDefinition = "json")
    private List<Map<String, String>> suggestions;

    /** 建议来源: llm / rule / fallback */
    @Column(name = "suggestion_source", nullable = false, length = 16)
    private String suggestionSource;

    /** LLM 模型名称，如 deepseek-chat */
    @Column(name = "suggestion_model", length = 64)
    private String suggestionModel;

    // ──────────── 时间戳 ────────────

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
