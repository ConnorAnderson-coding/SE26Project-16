package com.example.demo.scheduler;

import com.example.demo.common.CacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动生命周期调度：每日 00:30 把 endTime 已过但 status 仍滞后的活动批量切到 ended，
 * 实现"数据冻结"原则——活动结束后浏览/收藏/报名等动态指标立即停止更新。
 *
 * <p>与分析调度的关系：
 * <ul>
 *   <li>00:30 本类：把活动 status 切到 ended，并清空 ANALYTICS_ACTIVITY 缓存</li>
 *   <li>02:00 AnalysisScheduler：基于 endTime 扫描已结束活动派发 LLM 任务</li>
 * </ul>
 *
 * <p>为什么两者分开：
 * 分析任务按 endTime 区间扫描，不依赖 status 字段，避免污染主流程活动状态机；
 * 生命周期调度则专门维护 status 字段，供组织者后台 UI 区分"进行中 / 已结束"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLifecycleScheduler {

    private final com.example.demo.repository.ActivityRepository activityRepository;
    private final CacheManager cacheManager;

    @Scheduled(cron = "0 30 0 * * ?")
    @Transactional
    public void freezeEndedActivities() {
        LocalDateTime now = LocalDateTime.now();

        // 先用轻量查询拿到待冻结的 id 列表，用于精确清空缓存；
        // 再做批量 UPDATE 推进 status。两次操作非严格原子，但都在同一事务内，
        // 同一调度周期内并发场景可接受（幂等：status='ended' 后不再命中 WHERE 条件）。
        List<Long> idsToFreeze = activityRepository.findIdsToFreeze(now);
        if (idsToFreeze.isEmpty()) {
            log.info("[生命周期] 无待冻结活动");
            return;
        }

        int frozen = activityRepository.freezeEndedActivities(now);
        log.info("[生命周期] 已冻结 {} 个活动: {}", frozen, idsToFreeze);

        // 清空受影响活动的详情与分析缓存，避免下游读到 status 仍为旧值
        evictCaches(idsToFreeze);
    }

    private void evictCaches(List<Long> activityIds) {
        Cache detailCache = cacheManager.getCache(CacheNames.ACTIVITY_DETAIL);
        Cache analyticsCache = cacheManager.getCache(CacheNames.ANALYTICS_ACTIVITY);
        for (Long id : activityIds) {
            try {
                if (detailCache != null) detailCache.evict(id);
                if (analyticsCache != null) analyticsCache.evict(id);
            } catch (Exception e) {
                log.warn("[生命周期] 清空缓存失败: activityId={}, error={}", id, e.getMessage());
            }
        }
    }
}