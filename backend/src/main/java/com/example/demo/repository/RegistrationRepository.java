package com.example.demo.repository;

import com.example.demo.entity.Registration;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    @Query(value = "SELECT DATE(created_at) AS day, COUNT(*) AS cnt FROM registration " +
           "WHERE activity_id = :activityId " +
           "GROUP BY DATE(created_at) ORDER BY day", nativeQuery = true)
    List<Object[]> countDailySignupsByActivityId(@Param("activityId") Long activityId);

    long countByActivityIdAndStatus(Long activityId, String status);

    long countByUserId(String userId);

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
