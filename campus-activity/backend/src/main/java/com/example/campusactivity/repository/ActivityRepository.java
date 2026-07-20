package com.example.campusactivity.repository;

import com.example.campusactivity.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

public interface ActivityRepository extends JpaRepository<Activity, String> {
    List<Activity> findByOrganizerId(String organizerId);
    List<Activity> findByStatus(String status);
    List<Activity> findByCategory(String category);
    List<Activity> findByCreatedAtAfter(LocalDateTime dateTime);
    List<Activity> findByLastModifiedAtAfter(LocalDateTime dateTime);

    @Query("SELECT a FROM Activity a WHERE a.createdAt > :sevenDaysAgo OR a.lastModifiedAt > :oneHourAgo OR a.status = :inProgressStatus")
    List<Activity> findActivitiesToRecalculateHotness(
            LocalDateTime sevenDaysAgo,
            LocalDateTime oneHourAgo,
            String inProgressStatus
    );
}
