package com.example.campusactivity.repository;

import com.example.campusactivity.entity.Favorite;
import com.example.campusactivity.repository.projection.UserCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {
    List<Favorite> findByUserId(String userId);
    Optional<Favorite> findByUserIdAndActivityId(String userId, String activityId);
    boolean existsByUserIdAndActivityId(String userId, String activityId);

    @Query("""
            SELECT favorite.userId AS userId, COUNT(favorite) AS recordCount
            FROM Favorite favorite
            GROUP BY favorite.userId
            """)
    List<UserCountProjection> aggregateCountsByUserId();
}
