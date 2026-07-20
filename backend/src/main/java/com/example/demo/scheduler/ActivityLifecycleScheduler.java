package com.example.demo.scheduler;

import com.example.demo.common.CacheNames;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.search.ActivityIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLifecycleScheduler {

    private final ActivityRepository activityRepository;
    private final CacheManager cacheManager;
    private final ObjectProvider<ActivityIndexService> activityIndexService;

    @Scheduled(cron = "0 30 0 * * ?")
    @Transactional
    public void autoEndActivities() {
        LocalDateTime now = LocalDateTime.now();
        List<Activity> activities = activityRepository.findPublishedEndedBefore(now);
        if (!activities.isEmpty()) {
            activities.forEach(activity -> {
                activity.setStatus("ended");
                activity.setUpdatedAt(now);
            });
            activityRepository.saveAllAndFlush(activities);
            evictCaches(activities);
            syncSearchIndex(activities);
            log.info("活动生命周期任务：将 {} 个已过结束时间的活动标记为 ended (截止 {})", activities.size(), now);
        } else {
            log.debug("活动生命周期任务：无活动需要自动标记 (截止 {})", now);
        }
    }

    private void evictCaches(List<Activity> activities) {
        Cache detailCache = cacheManager.getCache(CacheNames.ACTIVITY_DETAIL);
        Cache analyticsCache = cacheManager.getCache(CacheNames.ANALYTICS_ACTIVITY);
        for (Activity activity : activities) {
            if (detailCache != null) detailCache.evict(activity.getId());
            if (analyticsCache != null) analyticsCache.evict(activity.getId());
        }
        Cache hotListCache = cacheManager.getCache(CacheNames.ACTIVITY_HOT_LIST);
        if (hotListCache != null) hotListCache.clear();
    }

    private void syncSearchIndex(List<Activity> activities) {
        ActivityIndexService indexService = activityIndexService.getIfAvailable();
        if (indexService == null) return;
        for (Activity activity : activities) {
            try {
                indexService.indexActivity(activity);
            } catch (RuntimeException ex) {
                log.warn("活动 {} 状态已更新，但同步搜索索引失败: {}", activity.getId(), ex.getMessage());
            }
        }
    }
}
