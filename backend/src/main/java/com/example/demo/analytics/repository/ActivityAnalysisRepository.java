package com.example.demo.analytics.repository;

import com.example.demo.analytics.entity.ActivityAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 活动分析结果仓库
 */
public interface ActivityAnalysisRepository extends JpaRepository<ActivityAnalysis, Long> {

    /** 按活动ID查询分析结果 */
    Optional<ActivityAnalysis> findByActivityId(Long activityId);

    /** 检查某活动是否已有分析结果 */
    boolean existsByActivityId(Long activityId);

    /** 按生成时间降序查询所有分析结果 */
    List<ActivityAnalysis> findAllByOrderByGeneratedAtDesc();
}
