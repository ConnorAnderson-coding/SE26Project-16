package com.example.demo.repository;

import com.example.demo.entity.CheckIn;
import com.example.demo.repository.projection.UserBehaviorCount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    boolean existsByActivityIdAndUserId(Long activityId, String userId);

    Optional<CheckIn> findByActivityIdAndUserId(Long activityId, String userId);

    @EntityGraph(attributePaths = {"activity", "user"})
    List<CheckIn> findByActivityIdOrderByCheckedAtDesc(Long activityId);

    @EntityGraph(attributePaths = {"activity", "user"})
    List<CheckIn> findByUserIdOrderByCheckedAtDesc(String userId);

    long countByActivityId(Long activityId);

    @Query("SELECT c.method, COUNT(c) FROM CheckIn c WHERE c.activity.id = :activityId GROUP BY c.method")
    List<Object[]> countByMethodGroupByActivityId(@Param("activityId") Long activityId);

    @Query("""
            SELECT c.user.id AS userId, COUNT(c) AS totalCount
            FROM CheckIn c
            WHERE c.checkedAt >= :from
            GROUP BY c.user.id
            """)
    List<UserBehaviorCount> countByUserSince(@Param("from") LocalDateTime from);
}
