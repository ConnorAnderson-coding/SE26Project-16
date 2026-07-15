package com.example.campusactivity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "clustering_runs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_clustering_runs_version", columnNames = "run_version")
        },
        indexes = {
                @Index(
                        name = "idx_clustering_runs_status_finished",
                        columnList = "run_status, finished_at"
                )
        }
)
@Check(name = "ck_clustering_runs_cluster_count", constraints = "cluster_count >= 2")
@Check(name = "ck_clustering_runs_algorithm", constraints = "algorithm_name = 'KMEANS'")
@Check(name = "ck_clustering_runs_random_state", constraints = "random_state = 42")
@Check(
        name = "ck_clustering_runs_sample_count",
        constraints = "sample_count IS NULL OR sample_count >= 0"
)
public class ClusteringRun {
    @Id
    @NotBlank
    @Size(max = 64)
    @Column(name = "run_id", length = 64, nullable = false)
    private String id;

    @NotBlank
    @Size(max = 64)
    @Column(name = "run_version", length = 64, nullable = false, updatable = false)
    private String version;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "algorithm_name", length = 32, nullable = false)
    private ClusteringAlgorithm algorithm;

    @NotNull
    @Min(2)
    @Column(name = "cluster_count", nullable = false)
    private Integer clusterCount;

    @NotNull
    @Column(name = "random_state", nullable = false)
    private Integer randomState;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "run_status", length = 16, nullable = false)
    private ClusteringRunStatus status;

    @PositiveOrZero
    @Column(name = "sample_count", nullable = true)
    private Integer sampleCount;

    @NotBlank
    @Size(max = 64)
    @Column(name = "feature_schema_version", length = 64, nullable = false)
    private String featureSchemaVersion;

    @Lob
    @NotNull
    @Column(name = "parameters_json", nullable = false, updatable = false)
    private String parametersJson;

    @Lob
    @Column(name = "metrics_json")
    private String metricsJson;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Size(max = 1000)
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @NotBlank
    @Size(max = 255)
    @Column(name = "created_by", length = 255, nullable = false)
    private String createdBy;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public ClusteringAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(ClusteringAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public Integer getClusterCount() {
        return clusterCount;
    }

    public void setClusterCount(Integer clusterCount) {
        this.clusterCount = clusterCount;
    }

    public Integer getRandomState() {
        return randomState;
    }

    public void setRandomState(Integer randomState) {
        this.randomState = randomState;
    }

    public ClusteringRunStatus getStatus() {
        return status;
    }

    public void setStatus(ClusteringRunStatus status) {
        this.status = status;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
    }

    public String getFeatureSchemaVersion() {
        return featureSchemaVersion;
    }

    public void setFeatureSchemaVersion(String featureSchemaVersion) {
        this.featureSchemaVersion = featureSchemaVersion;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public String getMetricsJson() {
        return metricsJson;
    }

    public void setMetricsJson(String metricsJson) {
        this.metricsJson = metricsJson;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
