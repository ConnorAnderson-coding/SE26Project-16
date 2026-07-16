package com.example.demo.repository;

import com.example.demo.entity.ActivityAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityAnalysisRepository extends JpaRepository<ActivityAnalysis, Long> {

    Optional<ActivityAnalysis> findByActivityId(Long activityId);

    boolean existsByActivityId(Long activityId);

    List<ActivityAnalysis> findAllByOrderByGeneratedAtDesc();
}
