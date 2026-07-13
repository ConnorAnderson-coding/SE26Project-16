package com.example.demo.analytics.repository;

import com.example.demo.analytics.entity.AnalysisScheduleLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 分析任务调度日志仓库
 */
public interface AnalysisScheduleLogRepository extends JpaRepository<AnalysisScheduleLog, Long> {

    /** 按任务名查询最近一次执行记录 */
    Optional<AnalysisScheduleLog> findTopByJobNameOrderByStartedAtDesc(String jobName);
}
