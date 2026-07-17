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

    
    @Column(name = "analysis_status", nullable = false, length = 16)
    private String analysisStatus = "ready";

    
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ==================== 一次性快照字段 ====================
    // 活动首次加入分析列表时一次性读取浏览量/报名量/收藏量并冻结
    // 之后不再随 activity 行的实时变化而改变
    // 分析任务首次生成时写入，后续展示直接读取该快照

    @Column(name = "view_count_snapshot")
    private Integer viewCountSnapshot;

    @Column(name = "signup_count_snapshot")
    private Integer signupCountSnapshot;

    @Column(name = "favorite_count_snapshot")
    private Integer favoriteCountSnapshot;

    @Column(name = "snapshot_at")
    private LocalDateTime snapshotAt;
}
