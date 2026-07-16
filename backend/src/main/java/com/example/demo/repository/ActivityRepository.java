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

    /**
     * 按 ID 加载活动完整视图（含 organizer 与 record）。
     * <p>
     * 结果会写入 {@link CacheNames#ACTIVITY_DETAIL} 缓存，
     * 但缓存中的 {@code viewCount} 字段可能落后于数据库；
     * 调用方应在写入 DTO 时叠加本次自增的最新值。
     *
     * @param id 活动主键
     * @return 活动实体
     */
    @EntityGraph(attributePaths = {"organizer", "record"})
    @Cacheable(value = CacheNames.ACTIVITY_DETAIL, key = "#id")
    Optional<Activity> findWithDetailsById(Long id);

    /**
     * 原子自增活动浏览量 +1。
     * <p>
     * 使用 JPQL 走数据库原子更新，避免并发竞态；并彻底脱离 @Cacheable 缓存层，
     * 保证后续 getById 读取到的 viewCount 始终是最新值。
     *
     * @param id 活动 ID
     * @return 受影响的行数（0 表示活动不存在）
     */
    @Modifying
    @Query("UPDATE Activity a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
    int incrementViewCount(@Param("id") Long id);

    @EntityGraph(attributePaths = "organizer")
    List<Activity> findByOrganizerIdOrderByStartTimeDesc(String organizerId);

    @EntityGraph(attributePaths = "organizer")
    @Query("SELECT a FROM Activity a WHERE a.status = 'published' ORDER BY a.signupCount DESC, a.favoriteCount DESC")
    @Cacheable(
            value = CacheNames.ACTIVITY_HOT_LIST,
            key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    List<Activity> findPublishedByHot(Pageable pageable);

    /** 查询指定时间范围内结束的活动（用于定时分析任务） */
    @Query("SELECT a FROM Activity a WHERE a.status = 'ended' " +
           "AND a.endTime >= :since AND a.endTime < :until " +
           "ORDER BY a.endTime ASC")
    List<Activity> findEndedBetween(
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until);

    /**
     * 查询所有已结束的活动，按结束时间升序。
     * <p>
     * 由 AnalysisScheduler 使用，每日凌晨扫描并刷新分析数据。
     */
    @Query("SELECT a FROM Activity a WHERE a.status = 'ended' ORDER BY a.endTime ASC")
    List<Activity> findAllEnded();

    /**
     * 自动结束活动：将所有已过结束时间但状态仍为 "published" 的活动标记为 "ended"。
     * <p>
     * 由 ActivityLifecycleScheduler 在每日凌晨调用，避免依赖组织者手动发布活动记录。
     *
     * @param now 当前时间
     * @return 受影响的行数
     */
    @Modifying
    @Query("UPDATE Activity a SET a.status = 'ended', a.updatedAt = :now " +
           "WHERE a.status = 'published' AND a.endTime < :now")
    int markEndedBefore(@Param("now") LocalDateTime now);

    /**
     * 拉取单个活动"最新数据时间戳"快照，覆盖所有可能影响分析结果的表：
     * activity 自身 / feedback / check_in / registration。
     * <p>
     * 用于定时任务判断"上次分析之后是否有新数据"，避免每次都重算 LLM。
     * 返回单行 4 列：[activity_updated_at, max_feedback_at, max_checkin_at, max_registration_at]，
     * 任意列可能为 NULL（对应表无记录）。
     */
    @Query(value = "SELECT a.updated_at, " +
           "(SELECT MAX(f.created_at) FROM feedback f WHERE f.activity_id = :id), " +
           "(SELECT MAX(c.check_in_time) FROM check_in c WHERE c.activity_id = :id), " +
           "(SELECT MAX(r.created_at) FROM registration r WHERE r.activity_id = :id) " +
           "FROM activity a WHERE a.id = :id", nativeQuery = true)
    List<Object[]> findDataFreshness(@Param("id") Long id);
}
