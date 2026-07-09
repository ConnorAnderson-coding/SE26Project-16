package com.example.campusactivity.repository;

import com.example.campusactivity.entity.Signup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SignupRepository extends JpaRepository<Signup, String> {
    List<Signup> findByActivityId(String activityId);
    List<Signup> findByUserId(String userId);
    Optional<Signup> findByActivityIdAndUserId(String activityId, String userId);
}
