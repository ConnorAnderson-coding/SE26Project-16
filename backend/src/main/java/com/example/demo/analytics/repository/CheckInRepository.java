package com.example.demo.analytics.repository;

import com.example.demo.analytics.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 签到记录仓库
 */
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    /** 按活动ID统计签到人数 */
    long countByActivityId(Long activityId);

    /** 按活动ID查询所有签到记录 */
    List<CheckIn> findByActivityId(Long activityId);

    /** 按用户ID查询签到记录 */
    List<CheckIn> findByUserIdOrderByCheckInTimeDesc(String userId);

    /** 检查某用户是否已签到某活动 */
    boolean existsByActivityIdAndUserId(Long activityId, String userId);

    /** 统计各签到方式人数 */
    @Query("SELECT c.method, COUNT(c) FROM CheckIn c " +
           "WHERE c.activityId = :activityId GROUP BY c.method")
    List<Object[]> countByMethodGroupByActivityId(@Param("activityId") Long activityId);
}
