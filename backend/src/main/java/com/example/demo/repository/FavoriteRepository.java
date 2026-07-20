package com.example.demo.repository;

import com.example.demo.entity.Favorite;
import com.example.demo.entity.FavoriteId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

    boolean existsByIdUserIdAndIdActivityId(String userId, Long activityId);

    @EntityGraph(attributePaths = {"activity", "activity.organizer"})
    List<Favorite> findByIdUserIdOrderByCreatedAtDesc(String userId);

    long countByIdUserId(String userId);

    long countByIdActivityId(Long activityId);
}
