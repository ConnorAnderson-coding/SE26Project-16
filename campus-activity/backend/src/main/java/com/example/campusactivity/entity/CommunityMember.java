package com.example.campusactivity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "community_memberships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_community_memberships_run_user",
                        columnNames = {"run_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_community_memberships_community_run",
                        columnList = "community_id, run_id"
                ),
                @Index(name = "idx_community_memberships_user", columnList = "user_id"),
                @Index(
                        name = "idx_community_memberships_admin_page",
                        columnList = "community_id, distance_to_center, user_id"
                )
        }
)
@Check(
        name = "ck_community_memberships_coordinate_x",
        constraints = "coordinate_x >= 0 AND coordinate_x <= 100"
)
@Check(
        name = "ck_community_memberships_coordinate_y",
        constraints = "coordinate_y >= 0 AND coordinate_y <= 100"
)
@Check(
        name = "ck_community_memberships_distance",
        constraints = "distance_to_center >= 0"
)
public class CommunityMember {
    @Id
    @NotBlank
    @Size(max = 64)
    @Column(name = "membership_id", length = 64, nullable = false)
    private String id;

    @NotNull
    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @NotNull
    @Column(name = "community_id", length = 64, nullable = false)
    private String communityId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "run_id",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_community_memberships_run")
    )
    private ClusteringRun run;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                    @JoinColumn(
                            name = "community_id",
                            referencedColumnName = "community_id",
                            nullable = false,
                            insertable = false,
                            updatable = false
                    ),
                    @JoinColumn(
                            name = "run_id",
                            referencedColumnName = "run_id",
                            nullable = false,
                            insertable = false,
                            updatable = false
                    )
            },
            foreignKey = @ForeignKey(name = "fk_community_memberships_community_run")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Community community;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_community_memberships_user")
    )
    private UserAccount user;

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
    private Instant assignedAt;

    @AssertTrue(message = "coordinates and distance must be finite")
    public boolean isFiniteValues() {
        return (coordinateX == null || Double.isFinite(coordinateX))
                && (coordinateY == null || Double.isFinite(coordinateY))
                && (distanceToCenter == null || Double.isFinite(distanceToCenter));
    }

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ClusteringRun getRun() {
        return run;
    }

    public void setRun(ClusteringRun run) {
        this.run = run;
        this.runId = run == null ? null : run.getId();
    }

    public Community getCommunity() {
        return community;
    }

    public void setCommunity(Community community) {
        this.community = community;
        this.communityId = community == null ? null : community.getId();
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
    }

    public Double getCoordinateX() {
        return coordinateX;
    }

    public void setCoordinateX(Double coordinateX) {
        this.coordinateX = coordinateX;
    }

    public Double getCoordinateY() {
        return coordinateY;
    }

    public void setCoordinateY(Double coordinateY) {
        this.coordinateY = coordinateY;
    }

    public Double getDistanceToCenter() {
        return distanceToCenter;
    }

    public void setDistanceToCenter(Double distanceToCenter) {
        this.distanceToCenter = distanceToCenter;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }
}
