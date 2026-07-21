package com.example.demo.repository;

import com.example.demo.common.CacheNames;
import com.example.demo.entity.Feedback;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    @EntityGraph(attributePaths = {"user", "activity"})
    @Cacheable(value = CacheNames.FEEDBACK_BY_ACTIVITY, key = "#activityId")
    List<Feedback> findByActivityIdOrderByCreatedAtDesc(Long activityId);

    @EntityGraph(attributePaths = "activity")
    List<Feedback> findByUserIdOrderByCreatedAtDesc(String userId);
}
