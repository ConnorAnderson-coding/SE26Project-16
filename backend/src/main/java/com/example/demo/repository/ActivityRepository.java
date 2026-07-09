package com.example.demo.repository;

import com.example.demo.common.CacheNames;
import com.example.demo.entity.Activity;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    @EntityGraph(attributePaths = "organizer")
    @Query("SELECT a FROM Activity a WHERE a.status <> 'draft' " +
           "AND (:category IS NULL OR a.category = :category) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:location IS NULL OR a.location = :location) " +
           "AND (:keyword IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(a.location) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Activity> search(
            @Param("category") String category,
            @Param("status") String status,
            @Param("location") String location,
            @Param("keyword") String keyword,
            Pageable pageable);

    @EntityGraph(attributePaths = {"organizer", "record"})
    @Cacheable(value = CacheNames.ACTIVITY_DETAIL, key = "#id")
    Optional<Activity> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = "organizer")
    List<Activity> findByOrganizerIdOrderByStartTimeDesc(String organizerId);

    @EntityGraph(attributePaths = "organizer")
    @Query("SELECT a FROM Activity a WHERE a.status = 'published' ORDER BY a.signupCount DESC, a.favoriteCount DESC")
    @Cacheable(
            value = CacheNames.ACTIVITY_HOT_LIST,
            key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    List<Activity> findPublishedByHot(Pageable pageable);
}
