package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "activity_view",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_activity_view_user", columnNames = {"activity_id", "user_id"}),
        indexes = @Index(name = "idx_activity_view_user", columnList = "user_id"))
public class ActivityView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "activity_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_view_activity"))
    private Activity activity;

    @Column(name = "user_id", nullable = false, length = 32)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_view_user"))
    private User user;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}
