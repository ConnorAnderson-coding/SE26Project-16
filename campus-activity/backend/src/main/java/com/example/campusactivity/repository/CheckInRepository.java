package com.example.campusactivity.repository;

import com.example.campusactivity.entity.CheckInRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckInRecord, String> {
    List<CheckInRecord> findByActivityId(String activityId);
    Long countByActivityId(String activityId);
    List<CheckInRecord> findByUserId(String userId);
    Optional<CheckInRecord> findByActivityIdAndUserId(String activityId, String userId);
}
