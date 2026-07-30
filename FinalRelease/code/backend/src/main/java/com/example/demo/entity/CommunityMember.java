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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "community_membership",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_community_membership_run_user",
                columnNames = {"run_id", "user_id"}
        ),
        indexes = {
                @Index(name = "idx_community_membership_community", columnList = "community_id, run_id"),
                @Index(name = "idx_community_membership_user", columnList = "user_id"),
                @Index(
                        name = "idx_community_membership_admin_page",
                        columnList = "community_id, distance_to_center, user_id"
                )
        },
        check = {
                @CheckConstraint(
                        name = "ck_community_membership_x",
                        constraint = "coordinate_x >= 0 AND coordinate_x <= 100"
                ),
                @CheckConstraint(
                        name = "ck_community_membership_y",
                        constraint = "coordinate_y >= 0 AND coordinate_y <= 100"
                ),
                @CheckConstraint(
                        name = "ck_community_membership_distance",
                        constraint = "distance_to_center >= 0"
                )
        }
)
public class CommunityMember {

    @Id
    @Column(name = "membership_id", length = 64, nullable = false, updatable = false)
    private String id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "run_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_community_membership_run")
    )
    private ClusteringRun run;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "community_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_community_membership_community")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Community community;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_community_membership_user")
    )
    private User user;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    @Column(name = "coordinate_x", nullable = false)
    private Double coordinateX;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    @Column(name = "coordinate_y", nullable = false)
    private Double coordinateY;

    @NotNull
    @DecimalMin("0.0")
    @Column(name = "distance_to_center", nullable = false)
    private Double distanceToCenter;

    @NotNull
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @AssertTrue(message = "coordinates and distance must be finite")
    public boolean isFiniteValues() {
        return (coordinateX == null || Double.isFinite(coordinateX))
                && (coordinateY == null || Double.isFinite(coordinateY))
                && (distanceToCenter == null || Double.isFinite(distanceToCenter));
    }

    @AssertTrue(message = "community must belong to the same clustering run")
    public boolean isRunConsistent() {
        if (run == null || community == null || community.getRun() == null) {
            return true;
        }
        return run.getId() != null && run.getId().equals(community.getRun().getId());
    }

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
    }
}
