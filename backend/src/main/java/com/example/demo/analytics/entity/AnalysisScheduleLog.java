package com.example.demo.analytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 分析任务调度日志实体
 * <p>
 * 记录每次分析任务的执行状态、耗时和错误信息，用于运维监控和问题排查。
 */
@Getter
@Setter
@Entity
@Table(name = "analysis_schedule_log",
       indexes = {
           @Index(name = "idx_log_job", columnList = "job_name, started_at")
       })
public class AnalysisScheduleLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 任务名称，如 analyzeEndedActivities */
    @Column(name = "job_name", nullable = false, length = 64)
    private String jobName;

    /** 关联活动ID — 定时批量任务时为 NULL，手动单活动触发时填入 */
    @Column(name = "activity_id")
    private Long activityId;

    /**
     * 执行状态:
     * <ul>
     *   <li>STARTED  — 任务已启动</li>
     *   <li>SUCCESS  — 执行成功</li>
     *   <li>FAILED   — 执行失败</li>
     *   <li>SKIPPED  — 已跳过（如活动已有分析结果）</li>
     * </ul>
     */
    @Column(nullable = false, length = 16)
    private String status;

    /** 执行耗时 (毫秒) */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /** 错误信息 */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** 任务开始时间 */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 任务结束时间 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
