package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "community_cluster",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_community_cluster_run_no", columnNames = {"run_id", "cluster_no"}),
                @UniqueConstraint(name = "uk_community_cluster_id_run", columnNames = {"community_id", "run_id"})
        },
        check = {
                @CheckConstraint(name = "ck_community_cluster_no", constraint = "cluster_no >= 0"),
                @CheckConstraint(name = "ck_community_cluster_members", constraint = "member_count > 0")
        }
)
public class Community {

    @Id
    @Column(name = "community_id", length = 64, nullable = false, updatable = false)
    private String id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "run_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_community_cluster_run")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClusteringRun run;

    @NotNull
    @PositiveOrZero
    @Column(name = "cluster_no", nullable = false, updatable = false)
    private Integer clusterNo;

    @NotBlank
    @Size(max = 100)
    @Column(name = "community_name", length = 100, nullable = false)
    private String name;

    @Size(max = 500)
    @Column(name = "community_description", length = 500)
    private String description;

    @NotNull
    @Positive
    @Column(name = "member_count", nullable = false)
    private Integer memberCount;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_interests_json", nullable = false, columnDefinition = "json")
    private List<String> topInterests = new ArrayList<>();

    @NotBlank
    @Size(max = 16)
    @Column(name = "display_color", length = 16, nullable = false)
    private String color;

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }
}
