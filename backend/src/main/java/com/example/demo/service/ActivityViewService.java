package com.example.demo.service;

import com.example.demo.common.CacheNames;
import com.example.demo.config.ActivityViewExecutorConfig;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.ActivityViewRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Unique-view accounting is intentionally off the detail GET critical path.
 * Callers should prefer {@link #recordUniqueViewAsync} so response latency is
 * not gated by activity-row locks under first-browse stampedes.
 */
@Slf4j
@Service
public class ActivityViewService {

    private static final int VIEW_INCREMENT_MAX_ATTEMPTS = 3;

    private final ActivityViewRepository activityViewRepository;
    private final ActivityRepository activityRepository;
    private final ActivityViewService self;

    public ActivityViewService(
            ActivityViewRepository activityViewRepository,
            ActivityRepository activityRepository,
            @Lazy ActivityViewService self) {
        this.activityViewRepository = activityViewRepository;
        this.activityRepository = activityRepository;
        this.self = self != null ? self : this;
    }

    @Async(ActivityViewExecutorConfig.VIEW_EXECUTOR)
    public void recordUniqueViewAsync(Long activityId, String userId) {
        try {
            self.recordUniqueView(activityId, userId);
        } catch (RuntimeException ex) {
            log.warn("async recordUniqueView failed activityId={}: {}", activityId, ex.toString());
        }
    }

    /**
     * @return true when a new unique view was recorded (analytics cache should refresh)
     */
    @Transactional
    @CacheEvict(value = CacheNames.ANALYTICS_ACTIVITY, key = "#activityId", condition = "#result")
    public boolean recordUniqueView(Long activityId, String userId) {
        // 数据冻结：活动已结束后不再记录浏览，避免污染分析指标。
        // Lock the parent row before inserting the child view record. Otherwise
        // concurrent inserts hold foreign-key shared locks and then deadlock
        // while all transactions try to upgrade the same activity row to write
        // view_count.
        //
        // Detail cache is NOT evicted here: view_count is a soft metric; thrashing
        // ACTIVITY_DETAIL under first-browse stampedes hurts latency more than a
        // briefly stale counter in the cached payload.
        Activity activity = activityRepository.findByIdForUpdate(activityId).orElse(null);
        if (activity == null || "ended".equals(activity.getStatus())) {
            return false;
        }
        int inserted = activityViewRepository.insertIfAbsent(
                activityId, userId, LocalDateTime.now());
        if (inserted <= 0) {
            return false;
        }
        incrementViewCountWithRetry(activityId);
        return true;
    }

    private void incrementViewCountWithRetry(Long activityId) {
        for (int attempt = 1; attempt <= VIEW_INCREMENT_MAX_ATTEMPTS; attempt++) {
            try {
                activityRepository.incrementViewCount(activityId);
                return;
            } catch (DeadlockLoserDataAccessException | CannotAcquireLockException ex) {
                if (attempt == VIEW_INCREMENT_MAX_ATTEMPTS) {
                    throw ex;
                }
                log.warn("view_count deadlock activityId={} attempt={}/{}, retrying",
                        activityId, attempt, VIEW_INCREMENT_MAX_ATTEMPTS);
                try {
                    Thread.sleep(15L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }
}
