package com.example.demo.repository;

import com.example.demo.common.CacheNames;
import com.example.demo.entity.Activity;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

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

    /** 原子自增活动浏览量，避免并发访问丢失计数。 */
    @Modifying
    @Query("UPDATE Activity a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
    int incrementViewCount(@Param("id") Long id);

    /** 原子自增签到数，避免并发访问丢失计数。 */
    @Modifying
    @Query("UPDATE Activity a SET a.checkInCount = a.checkInCount + 1, " +
           "a.updatedAt = :now WHERE a.id = :id")
    int incrementCheckInCount(@Param("id") Long id, @Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = "organizer")
    List<Activity> findByOrganizerIdOrderByStartTimeDesc(String organizerId);

    @EntityGraph(attributePaths = "organizer")
    @Query("SELECT a FROM Activity a WHERE a.status <> 'draft' ORDER BY a.id")
    List<Activity> findAllIndexable();

    @EntityGraph(attributePaths = "organizer")
    @Query("SELECT a FROM Activity a WHERE a.status = 'published' ORDER BY a.hotnessScore DESC")
    @Cacheable(
            value = CacheNames.ACTIVITY_HOT_LIST,
            key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    List<Activity> findPublishedByHot(Pageable pageable);

    /**
     * 查询指定时间窗口内结束的活动（按 endTime，不依赖 status，避免改动主流程活动状态机）。
     * 用于定时分析任务。
     */
    @Query("SELECT a FROM Activity a WHERE a.endTime >= :since AND a.endTime < :until " +
           "AND a.status <> 'draft' ORDER BY a.endTime ASC")
    List<Activity> findEndedBetween(
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until);

    /**
     * 列出所有 endTime 已过但 status 尚未推进到 ended 的活动 id。
     * 用于 00:30 生命周期调度，把"已结束但状态滞后"的活动批量切到 ended。
     * 排除 cancelled（已被组织者取消，无须再推进）与 draft（从未发布）。
     */
    @Query("SELECT a.id FROM Activity a WHERE a.endTime < :now " +
           "AND a.status NOT IN ('ended', 'cancelled', 'draft')")
    List<Long> findIdsToFreeze(@Param("now") LocalDateTime now);

    /**
     * 批量将 endTime 已过且状态滞后的活动切到 ended。
     * 排除 cancelled 与 draft；返回受影响行数。
     */
    @Modifying
    @Query("UPDATE Activity a SET a.status = 'ended' " +
           "WHERE a.endTime < :now " +
           "AND a.status NOT IN ('ended', 'cancelled', 'draft')")
    int freezeEndedActivities(@Param("now") LocalDateTime now);

    @Query(value = "SELECT a.updated_at, " +
           "(SELECT MAX(f.created_at) FROM feedback f WHERE f.activity_id = :id), " +
           "(SELECT MAX(c.checked_at) FROM check_in c WHERE c.activity_id = :id), " +
           "(SELECT MAX(r.created_at) FROM registration r WHERE r.activity_id = :id) " +
           "FROM activity a WHERE a.id = :id", nativeQuery = true)
    List<Object[]> findDataFreshness(@Param("id") Long id);

    @EntityGraph(attributePaths = "organizer")
    List<Activity> findByIdIn(Collection<Long> ids);
}
