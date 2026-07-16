package com.example.campusactivity.repository;

import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.projection.UserCollectionValueProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByIdAndPassword(String id, String password);

    List<UserAccount> findAllByOrderByIdAsc();

    @Query("""
            SELECT user.id AS userId, interest AS collectionValue
            FROM UserAccount user
            JOIN user.interests interest
            """)
    List<UserCollectionValueProjection> findAllInterestValues();

    @Query("""
            SELECT user.id AS userId, availableTime AS collectionValue
            FROM UserAccount user
            JOIN user.availableTime availableTime
            """)
    List<UserCollectionValueProjection> findAllAvailableTimeValues();
}
