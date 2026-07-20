package com.example.demo.repository;

import com.example.demo.common.CacheNames;
import com.example.demo.entity.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    /**
     * 带 Redis 缓存的用户查询（不含密码，供业务读取使用）。
     */
    @Cacheable(value = CacheNames.USER_PROFILE, key = "#id")
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findCachedById(@Param("id") String id);

    Optional<User> findByJaccount(String jaccount);

    boolean existsById(String id);
}
