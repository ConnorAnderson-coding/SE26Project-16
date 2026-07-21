package com.example.campusactivity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(
        name = "community_clusters",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_community_clusters_run_cluster",
                        columnNames = {"run_id", "cluster_no"}
                ),
                @UniqueConstraint(
                        name = "uk_community_clusters_id_run",
                        columnNames = {"community_id", "run_id"}
                )
        }
)
@Check(name = "ck_community_clusters_cluster_no", constraints = "cluster_no >= 0")
@Check(name = "ck_community_clusters_member_count", constraints = "member_count > 0")
public class Community {
    @Id
    @NotBlank
    @Size(max = 64)
    @Column(name = "community_id", length = 64, nullable = false)
    private String id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "run_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_community_clusters_run")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClusteringRun run;

    @NotNull
    @PositiveOrZero
    @Column(name = "cluster_no", nullable = false)
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

    @Lob
    @NotNull
    @Column(name = "top_interests_json", nullable = false)
    private String topInterestsJson = "[]";

    @NotBlank
    @Size(max = 16)
    @Column(name = "display_color", length = 16, nullable = false)
    private String color;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
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
    }

    public Integer getClusterNo() {
        return clusterNo;
    }

    public void setClusterNo(Integer clusterNo) {
        this.clusterNo = clusterNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public String getTopInterestsJson() {
        return topInterestsJson;
    }

    public void setTopInterestsJson(String topInterestsJson) {
        this.topInterestsJson = topInterestsJson;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
