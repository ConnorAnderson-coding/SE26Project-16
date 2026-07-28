package com.example.demo.repository;

import com.example.demo.entity.Favorite;
import com.example.demo.entity.FavoriteId;
import com.example.demo.repository.projection.UserBehaviorCount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

    boolean existsByIdUserIdAndIdActivityId(String userId, Long activityId);

    @EntityGraph(attributePaths = {"activity", "activity.organizer"})
    List<Favorite> findByIdUserIdOrderByCreatedAtDesc(String userId);

    long countByIdUserId(String userId);

    @Query("""
            SELECT f.user.id AS userId, COUNT(f) AS totalCount
            FROM Favorite f
            WHERE f.createdAt >= :from
            GROUP BY f.user.id
            """)
    List<UserBehaviorCount> countByUserSince(@Param("from") LocalDateTime from);
}
