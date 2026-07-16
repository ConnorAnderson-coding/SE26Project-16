package com.example.demo.repository;

import com.example.demo.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    long countByActivityId(Long activityId);

    List<CheckIn> findByActivityId(Long activityId);

    List<CheckIn> findByUserIdOrderByCheckInTimeDesc(String userId);

    boolean existsByActivityIdAndUserId(Long activityId, String userId);

    @Query("SELECT c.method, COUNT(c) FROM CheckIn c " +
           "WHERE c.activityId = :activityId GROUP BY c.method")
    List<Object[]> countByMethodGroupByActivityId(@Param("activityId") Long activityId);
}
