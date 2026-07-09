package com.example.campusactivity.repository;

import com.example.campusactivity.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, String> {
    List<Activity> findByOrganizerId(String organizerId);
    List<Activity> findByStatus(String status);
    List<Activity> findByCategory(String category);
}
