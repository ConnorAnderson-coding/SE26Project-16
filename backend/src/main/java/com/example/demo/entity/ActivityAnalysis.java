package com.example.demo.entity;

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

    @Column(name = "signup_rate", nullable = false, precision = 5, scale = 1)
    private BigDecimal signupRate;

    @Column(name = "attendance_rate", nullable = false, precision = 5, scale = 1)
    private BigDecimal attendanceRate;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    private BigDecimal avgRating;

    @Column(name = "favorite_conversion", precision = 5, scale = 1)
    private BigDecimal favoriteConversion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rating_distribution", columnDefinition = "json")
    private Map<Integer, Long> ratingDistribution;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "check_in_methods_stats", columnDefinition = "json")
    private Map<String, Long> checkInMethodsStats;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json", columnDefinition = "json")
    private Map<String, Object> metricsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestions", columnDefinition = "json")
    private List<Map<String, String>> suggestions;

    @Column(name = "suggestion_source", nullable = false, length = 16)
    private String suggestionSource;

    @Column(name = "suggestion_model", length = 64)
    private String suggestionModel;

    /**
     * 异步分析任务状态：pending / ready / failed。
     * <p>
     * pending：任务已提交但 LLM 调用尚未完成；
     * ready：成功落库；
     * failed：重试后仍失败，已写入规则模板兜底。
     */
    @Column(name = "analysis_status", nullable = false, length = 16)
    private String analysisStatus = "ready";

    /**
     * 失败原因（最后一次不可恢复异常），便于审计。
     */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
