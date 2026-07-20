package com.example.campusactivity.repository;

import com.example.campusactivity.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByIdAndPassword(String id, String password);
}
