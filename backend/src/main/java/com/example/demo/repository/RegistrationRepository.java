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

    long countByUserId(String userId);
}
