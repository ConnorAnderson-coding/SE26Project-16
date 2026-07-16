package com.example.campusactivity.repository;

import com.example.campusactivity.entity.Feedback;
import com.example.campusactivity.repository.projection.FeedbackAggregateProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {
    List<Feedback> findByActivityId(String activityId);
    List<Feedback> findByUserId(String userId);

    @Query("""
            SELECT feedback.userId AS userId,
                   COUNT(feedback) AS feedbackCount,
                   SUM(CASE
                       WHEN feedback.rating BETWEEN :minimumRating AND :maximumRating THEN 1
                       ELSE 0
                   END) AS validRatingCount,
                   SUM(CASE
                       WHEN feedback.rating BETWEEN :minimumRating AND :maximumRating THEN feedback.rating
                       ELSE 0
                   END) AS validRatingSum,
                   SUM(CASE
                       WHEN feedback.rating IS NOT NULL
                            AND (feedback.rating < :minimumRating OR feedback.rating > :maximumRating) THEN 1
                       ELSE 0
                   END) AS invalidRatingCount
            FROM Feedback feedback
            GROUP BY feedback.userId
            """)
    List<FeedbackAggregateProjection> aggregateByUserId(
            @Param("minimumRating") Integer minimumRating,
            @Param("maximumRating") Integer maximumRating
    );
}
