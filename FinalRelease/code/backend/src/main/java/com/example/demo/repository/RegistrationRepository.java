package com.example.demo.repository;

import com.example.demo.entity.Registration;
import com.example.demo.repository.projection.UserCategoryCount;
import com.example.demo.repository.projection.UserRegistrationAggregate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    boolean existsByActivityIdAndUserId(Long activityId, String userId);

    Optional<Registration> findByActivityIdAndUserId(Long activityId, String userId);

    @EntityGraph(attributePaths = {"activity", "activity.organizer", "user"})
    List<Registration> findByUserIdOrderByCreatedAtDesc(String userId);

    @EntityGraph(attributePaths = {"activity", "user"})
    @Query("SELECT r FROM Registration r WHERE r.activity.organizer.id = :organizerId " +
           "AND (:activityId IS NULL OR r.activity.id = :activityId) ORDER BY r.createdAt DESC")
    List<Registration> findByOrganizer(
            @Param("organizerId") String organizerId,
            @Param("activityId") Long activityId);

    long countByUserIdAndStatus(String userId, String status);

    // 别名 signup_date 避开 H2 'DAY' 保留字（MySQL 默认宽松，但 H2 即使在 MODE=MySQL 下也保留）。
    // 消费方 AnalyticsEngine#computeSignupTrend 按列下标 row[0]/row[1] 读取，别名仅用于 ORDER BY。
    @Query(value = "SELECT DATE(created_at) AS signup_date, COUNT(*) AS cnt FROM registration " +
           "WHERE activity_id = :activityId " +
           "GROUP BY DATE(created_at) ORDER BY signup_date", nativeQuery = true)
    List<Object[]> countDailySignupsByActivityId(@Param("activityId") Long activityId);

    long countByActivityIdAndStatus(Long activityId, String status);

    long countByActivityId(Long activityId);

    long countByUserId(String userId);

    @Query("""
            SELECT r.user.id AS userId,
                   COUNT(r) AS totalCount,
                   SUM(CASE WHEN r.status = 'approved' THEN 1 ELSE 0 END) AS approvedCount
            FROM Registration r
            WHERE r.createdAt >= :from
            GROUP BY r.user.id
            """)
    List<UserRegistrationAggregate> aggregateByUserSince(@Param("from") LocalDateTime from);

    @Query("""
            SELECT r.user.id AS userId,
                   r.activity.category AS category,
                   COUNT(r) AS totalCount
            FROM Registration r
            WHERE r.createdAt >= :from AND r.status = 'approved'
            GROUP BY r.user.id, r.activity.category
            """)
    List<UserCategoryCount> countApprovedCategoriesByUserSince(@Param("from") LocalDateTime from);

    @Query("""
            SELECT COUNT(DISTINCT r1.activity.id) FROM Registration r1, Registration r2
            WHERE r1.user.id = :userA AND r2.user.id = :userB
              AND r1.activity.id = r2.activity.id
              AND (r1.status IS NULL OR r1.status <> 'rejected')
              AND (r2.status IS NULL OR r2.status <> 'rejected')
            """)
    long countCoParticipation(@Param("userA") String userA, @Param("userB") String userB);

    @Query("""
            SELECT COUNT(r) FROM Registration r
            WHERE r.user.id = :userId
              AND r.activity.organizer.id = :organizerId
              AND (r.status IS NULL OR r.status <> 'rejected')
            """)
    long countUserSignedOrganizer(
            @Param("userId") String userId,
            @Param("organizerId") String organizerId);
}
