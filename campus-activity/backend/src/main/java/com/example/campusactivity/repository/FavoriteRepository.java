package com.example.campusactivity.repository;

import com.example.campusactivity.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {
    List<Favorite> findByUserId(String userId);
    Optional<Favorite> findByUserIdAndActivityId(String userId, String activityId);
    boolean existsByUserIdAndActivityId(String userId, String activityId);
}
