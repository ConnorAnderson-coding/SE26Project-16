package com.example.demo.analytics.entity;

import com.example.demo.entity.Activity;
import com.example.demo.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 签到记录实体
 *
 * @see Activity 关联活动
 * @see User 关联用户
 */
@Getter
@Setter
@Entity
@Table(name = "check_in")
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false,
            insertable = false, updatable = false)
    private Long activityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(name = "user_id", nullable = false, length = 32,
            insertable = false, updatable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 签到方式: qrcode(二维码) / location(定位) / password(口令)
     */
    @Column(nullable = false, length = 16)
    private String method;

    @Column(name = "check_in_time", nullable = false)
    private LocalDateTime checkInTime;
}
