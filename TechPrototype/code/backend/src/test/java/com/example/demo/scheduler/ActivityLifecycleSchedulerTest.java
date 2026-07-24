package com.example.demo.scheduler;

import com.example.demo.common.CacheNames;
import com.example.demo.repository.ActivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 活动生命周期调度器测试。
 *
 * 目标：
 * 1) 待冻结 id 列表为空时不调用 UPDATE
 * 2) 正常路径：调用 UPDATE 推进 status + 清空 ANALYTICS_ACTIVITY 与 ACTIVITY_DETAIL 缓存
 * 3) 缓存缺失（CacheManager 返回 null）时仍能容忍完成 UPDATE
 */
@ExtendWith(MockitoExtension.class)
class ActivityLifecycleSchedulerTest {

    @Mock ActivityRepository activityRepository;
    @Mock CacheManager cacheManager;
    @InjectMocks ActivityLifecycleScheduler scheduler;

    @Test
    void noopWhenNoActivitiesToFreeze() {
        when(activityRepository.findIdsToFreeze(any())).thenReturn(List.of());

        scheduler.freezeEndedActivities();

        verify(activityRepository, never()).freezeEndedActivities(any());
        verifyNoInteractions(cacheManager);
    }

    @Test
    void freezesAndEvictsCachesForAffectedActivities() {
        List<Long> ids = List.of(101L, 102L, 103L);
        when(activityRepository.findIdsToFreeze(any())).thenReturn(ids);
        when(activityRepository.freezeEndedActivities(any())).thenReturn(3);

        Cache detailCache = mock(Cache.class);
        Cache analyticsCache = mock(Cache.class);
        when(cacheManager.getCache(CacheNames.ACTIVITY_DETAIL)).thenReturn(detailCache);
        when(cacheManager.getCache(CacheNames.ANALYTICS_ACTIVITY)).thenReturn(analyticsCache);

        scheduler.freezeEndedActivities();

        // 触发一次 UPDATE，参数 now 由调度器内部传入，只校验被调用
        verify(activityRepository, times(1)).freezeEndedActivities(any(LocalDateTime.class));
        // 受影响 id 全部清空两类缓存
        verify(detailCache, times(3)).evict(anyLong());
        verify(analyticsCache, times(3)).evict(anyLong());
        verify(detailCache).evict(eq(101L));
        verify(detailCache).evict(eq(102L));
        verify(detailCache).evict(eq(103L));
        verify(analyticsCache).evict(eq(101L));
        verify(analyticsCache).evict(eq(102L));
        verify(analyticsCache).evict(eq(103L));
    }

    @Test
    void toleratesMissingCacheBeans() {
        // 真实环境里若 Redis 不可用，CacheManager.getCache 可能返回 null；
        // 调度器应不抛异常、继续完成 UPDATE 推进 status。
        List<Long> ids = List.of(201L);
        when(activityRepository.findIdsToFreeze(any())).thenReturn(ids);
        when(activityRepository.freezeEndedActivities(any())).thenReturn(1);
        when(cacheManager.getCache(CacheNames.ACTIVITY_DETAIL)).thenReturn(null);
        when(cacheManager.getCache(CacheNames.ANALYTICS_ACTIVITY)).thenReturn(null);

        scheduler.freezeEndedActivities();

        verify(activityRepository).freezeEndedActivities(any(LocalDateTime.class));
        // 无缓存 bean 情况下不抛异常即可
    }
}