package com.example.demo.repository;

import com.example.demo.entity.ActivityAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityAnalysisRepository extends JpaRepository<ActivityAnalysis, Long> {

    Optional<ActivityAnalysis> findByActivityId(Long activityId);

    /** 查询所有规则模板来源的分析记录（用于定时任务升级为 LLM 建议） */
    List<ActivityAnalysis> findBySuggestionSource(String suggestionSource);
}
