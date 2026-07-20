package com.example.campusactivity.repository;

import com.example.campusactivity.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {
    List<Feedback> findByActivityId(String activityId);
    List<Feedback> findByUserId(String userId);
}
