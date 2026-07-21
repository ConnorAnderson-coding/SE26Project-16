package com.example.campusactivity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(
        name = "clustering_run_inputs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_clustering_run_inputs_run_user",
                        columnNames = {"run_id", "user_id"}
                ),
                @UniqueConstraint(
                        name = "uk_clustering_run_inputs_run_order",
                        columnNames = {"run_id", "sample_order"}
                )
        },
        indexes = @Index(
                name = "idx_clustering_run_inputs_run_order",
                columnList = "run_id, sample_order"
        )
)
@Check(
        name = "ck_clustering_run_inputs_counts",
        constraints = "signup_count >= 0 AND approved_signup_count >= 0 "
                + "AND approved_signup_count <= signup_count AND favorite_count >= 0 "
                + "AND check_in_count >= 0 AND feedback_count >= 0"
)
@Check(
        name = "ck_clustering_run_inputs_average_rating",
        constraints = "average_rating IS NULL OR "
                + "(average_rating >= 1.0 AND average_rating <= 5.0)"
)
public class ClusteringRunInput {
    @Id
    @NotBlank
    @Size(max = 64)
    @Column(name = "input_id", length = 64, nullable = false, updatable = false)
    private String id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "run_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_clustering_run_inputs_run")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClusteringRun run;

    @NotBlank
    @Size(max = 64)
    @Column(name = "user_id", length = 64, nullable = false, updatable = false)
    private String userId;

    @NotNull
    @PositiveOrZero
    @Column(name = "sample_order", nullable = false, updatable = false)
    private Integer sampleOrder;

    @Lob
    @NotNull
    @Column(name = "interests_json", nullable = false, updatable = false)
    private String interestsJson;

    @Size(max = 255)
    @Column(name = "college", length = 255, updatable = false)
    private String college;

    @Size(max = 255)
    @Column(name = "grade", length = 255, updatable = false)
    private String grade;

    @Lob
    @NotNull
    @Column(name = "available_time_json", nullable = false, updatable = false)
    private String availableTimeJson;

    @NotNull
    @Min(0)
    @Column(name = "signup_count", nullable = false, updatable = false)
    private Integer signupCount;

    @NotNull
    @Min(0)
    @Column(name = "approved_signup_count", nullable = false, updatable = false)
    private Integer approvedSignupCount;

    @NotNull
    @Min(0)
    @Column(name = "favorite_count", nullable = false, updatable = false)
    private Integer favoriteCount;

    @NotNull
    @Min(0)
    @Column(name = "check_in_count", nullable = false, updatable = false)
    private Integer checkInCount;

    @NotNull
    @Min(0)
    @Column(name = "feedback_count", nullable = false, updatable = false)
    private Integer feedbackCount;

    @Column(name = "average_rating", updatable = false)
    private Double averageRating;

    @Lob
    @NotNull
    @Column(
            name = "category_participation_counts_json",
            nullable = false,
            updatable = false
    )
    private String categoryParticipationCountsJson;

    protected ClusteringRunInput() {
    }

    public ClusteringRunInput(
            ClusteringRun run,
            String userId,
            int sampleOrder,
            String interestsJson,
            String college,
            String grade,
            String availableTimeJson,
            int signupCount,
            int approvedSignupCount,
            int favoriteCount,
            int checkInCount,
            int feedbackCount,
            Double averageRating,
            String categoryParticipationCountsJson
    ) {
        this.run = run;
        this.userId = userId;
        this.sampleOrder = sampleOrder;
        this.interestsJson = interestsJson;
        this.college = college;
        this.grade = grade;
        this.availableTimeJson = availableTimeJson;
        this.signupCount = signupCount;
        this.approvedSignupCount = approvedSignupCount;
        this.favoriteCount = favoriteCount;
        this.checkInCount = checkInCount;
        this.feedbackCount = feedbackCount;
        this.averageRating = averageRating;
        this.categoryParticipationCountsJson = categoryParticipationCountsJson;
    }

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() {
        return id;
    }

    public ClusteringRun getRun() {
        return run;
    }

    public String getUserId() {
        return userId;
    }

    public Integer getSampleOrder() {
        return sampleOrder;
    }

    public String getInterestsJson() {
        return interestsJson;
    }

    public String getCollege() {
        return college;
    }

    public String getGrade() {
        return grade;
    }

    public String getAvailableTimeJson() {
        return availableTimeJson;
    }

    public Integer getSignupCount() {
        return signupCount;
    }

    public Integer getApprovedSignupCount() {
        return approvedSignupCount;
    }

    public Integer getFavoriteCount() {
        return favoriteCount;
    }

    public Integer getCheckInCount() {
        return checkInCount;
    }

    public Integer getFeedbackCount() {
        return feedbackCount;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public String getCategoryParticipationCountsJson() {
        return categoryParticipationCountsJson;
    }
}
