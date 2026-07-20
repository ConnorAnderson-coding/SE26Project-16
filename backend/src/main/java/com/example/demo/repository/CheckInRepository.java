package com.example.demo.repository;

import com.example.demo.entity.CheckIn;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    boolean existsByActivityIdAndUserId(Long activityId, String userId);

    Optional<CheckIn> findByActivityIdAndUserId(Long activityId, String userId);

    @EntityGraph(attributePaths = {"activity", "user"})
    List<CheckIn> findByActivityIdOrderByCheckedAtDesc(Long activityId);

    @EntityGraph(attributePaths = {"activity", "user"})
    List<CheckIn> findByUserIdOrderByCheckedAtDesc(String userId);

    long countByActivityId(Long activityId);
}
