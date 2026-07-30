package com.example.demo.repository;

import com.example.demo.common.CacheNames;
import com.example.demo.entity.Feedback;
import com.example.demo.repository.projection.UserFeedbackAggregate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    @EntityGraph(attributePaths = {"user", "activity"})
    @Cacheable(value = CacheNames.FEEDBACK_BY_ACTIVITY, key = "#activityId")
    List<Feedback> findByActivityIdOrderByCreatedAtDesc(Long activityId);

    @EntityGraph(attributePaths = "activity")
    List<Feedback> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("""
            SELECT f.user.id AS userId,
                   COUNT(f) AS totalCount,
                   AVG(f.rating) AS averageRating
            FROM Feedback f
            WHERE f.createdAt >= :from
            GROUP BY f.user.id
            """)
    List<UserFeedbackAggregate> aggregateByUserSince(@Param("from") LocalDateTime from);
}
