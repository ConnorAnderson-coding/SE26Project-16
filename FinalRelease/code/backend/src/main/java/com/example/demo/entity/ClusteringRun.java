package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "clustering_run",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_clustering_run_version", columnNames = "run_version"),
                @UniqueConstraint(name = "uk_clustering_run_active_slot", columnNames = "active_slot")
        },
        indexes = {
                @Index(name = "idx_clustering_run_status_finished", columnList = "run_status, finished_at"),
                @Index(name = "idx_clustering_run_created_id", columnList = "created_at, run_id")
        },
        check = {
                @CheckConstraint(name = "ck_clustering_run_cluster_count", constraint = "cluster_count >= 2"),
                @CheckConstraint(name = "ck_clustering_run_algorithm", constraint = "algorithm_name = 'KMEANS'"),
                @CheckConstraint(name = "ck_clustering_run_random_state", constraint = "random_state = 42"),
                @CheckConstraint(
                        name = "ck_clustering_run_sample_count",
                        constraint = "sample_count IS NULL OR sample_count >= 0"
                ),
                @CheckConstraint(
                        name = "ck_clustering_run_feature_dimension",
                        constraint = "feature_dimension IS NULL OR feature_dimension > 0"
                ),
                @CheckConstraint(
                        name = "ck_clustering_run_active_slot_state",
                        constraint = "((run_status IN ('PENDING', 'RUNNING') AND active_slot = 'GLOBAL') "
                                + "OR (run_status IN ('SUCCESS', 'FAILED') AND active_slot IS NULL))"
                )
        }
)
public class ClusteringRun {

    public static final String GLOBAL_ACTIVE_SLOT = "GLOBAL";

    @Id
    @NotBlank
    @Size(max = 64)
    @Column(name = "run_id", length = 64, nullable = false, updatable = false)
    private String id;

    @NotBlank
    @Size(max = 64)
    @Column(name = "run_version", length = 64, nullable = false, updatable = false)
    private String version;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "algorithm_name", length = 32, nullable = false, updatable = false)
    private ClusteringAlgorithm algorithm = ClusteringAlgorithm.KMEANS;

    @NotNull
    @Min(2)
    @Column(name = "cluster_count", nullable = false, updatable = false)
    private Integer clusterCount;

    @NotNull
    @Column(name = "random_state", nullable = false, updatable = false)
    private Integer randomState = 42;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "run_status", length = 16, nullable = false)
    private ClusteringRunStatus status = ClusteringRunStatus.PENDING;

    @Size(max = 16)
    @Column(name = "active_slot", length = 16)
    private String activeSlot = GLOBAL_ACTIVE_SLOT;

    @PositiveOrZero
    @Column(name = "sample_count")
    private Integer sampleCount;

    @Positive
    @Column(name = "feature_dimension")
    private Integer featureDimension;

    @NotBlank
    @Size(max = 64)
    @Column(name = "feature_schema_version", length = 64, nullable = false, updatable = false)
    private String featureSchemaVersion;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json", nullable = false, updatable = false, columnDefinition = "json")
    private Map<String, Object> parameters = new LinkedHashMap<>();

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_manifest_json", nullable = false, updatable = false, columnDefinition = "json")
    private Map<String, Object> featureManifest = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json", columnDefinition = "json")
    private Map<String, Object> metrics;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Size(max = 64)
    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Size(max = 1000)
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @NotBlank
    @Size(max = 32)
    @Column(name = "created_by", length = 32, nullable = false, updatable = false)
    private String createdBy;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
