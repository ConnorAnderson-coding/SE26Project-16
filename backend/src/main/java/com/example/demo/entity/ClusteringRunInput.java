package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "clustering_run_input",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_clustering_run_input_user", columnNames = {"run_id", "user_id"}),
                @UniqueConstraint(name = "uk_clustering_run_input_order", columnNames = {"run_id", "sample_order"})
        },
        indexes = @Index(name = "idx_clustering_run_input_order", columnList = "run_id, sample_order"),
        check = @CheckConstraint(name = "ck_clustering_run_input_order", constraint = "sample_order >= 0")
)
public class ClusteringRunInput {

    @Id
    @Column(name = "input_id", length = 64, nullable = false, updatable = false)
    private String id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "run_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_clustering_run_input_run")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClusteringRun run;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_clustering_run_input_user")
    )
    private User user;

    @NotNull
    @PositiveOrZero
    @Column(name = "sample_order", nullable = false, updatable = false)
    private Integer sampleOrder;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_payload_json", nullable = false, updatable = false, columnDefinition = "json")
    private Map<String, Object> featurePayload = new LinkedHashMap<>();

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }
}
