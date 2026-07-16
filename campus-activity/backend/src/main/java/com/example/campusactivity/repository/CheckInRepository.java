package com.example.campusactivity.repository;

import com.example.campusactivity.entity.CheckInRecord;
import com.example.campusactivity.repository.projection.UserCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckInRecord, String> {
    List<CheckInRecord> findByActivityId(String activityId);
    List<CheckInRecord> findByUserId(String userId);
    Optional<CheckInRecord> findByActivityIdAndUserId(String activityId, String userId);

    @Query("""
            SELECT checkIn.userId AS userId, COUNT(checkIn) AS recordCount
            FROM CheckInRecord checkIn
            GROUP BY checkIn.userId
            """)
    List<UserCountProjection> aggregateCountsByUserId();
}
