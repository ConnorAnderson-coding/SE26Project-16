package com.example.demo.repository;

import com.example.demo.entity.ActivityView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ActivityViewRepository extends JpaRepository<ActivityView, Long> {

    long countByActivityId(Long activityId);

    @Modifying
    @Query(value = "INSERT IGNORE INTO activity_view (activity_id, user_id, viewed_at) " +
            "VALUES (:activityId, :userId, :viewedAt)", nativeQuery = true)
    int insertIfAbsent(
            @Param("activityId") Long activityId,
            @Param("userId") String userId,
            @Param("viewedAt") LocalDateTime viewedAt);
}
