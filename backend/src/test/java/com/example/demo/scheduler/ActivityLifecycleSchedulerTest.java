package com.example.demo.scheduler;

import com.example.demo.common.CacheNames;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.search.ActivityIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityLifecycleSchedulerTest {

    @Test
    void endingActivityEvictsRelatedCaches() {
        ActivityRepository repository = mock(ActivityRepository.class);
        CacheManager cacheManager = mock(CacheManager.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ActivityIndexService> indexProvider = mock(ObjectProvider.class);
        Cache detail = mock(Cache.class);
        Cache analytics = mock(Cache.class);
        Cache hotList = mock(Cache.class);

        Activity activity = new Activity();
        activity.setId(8L);
        activity.setStatus("published");
        when(repository.findPublishedEndedBefore(any(LocalDateTime.class))).thenReturn(List.of(activity));
        when(cacheManager.getCache(CacheNames.ACTIVITY_DETAIL)).thenReturn(detail);
        when(cacheManager.getCache(CacheNames.ANALYTICS_ACTIVITY)).thenReturn(analytics);
        when(cacheManager.getCache(CacheNames.ACTIVITY_HOT_LIST)).thenReturn(hotList);
        when(indexProvider.getIfAvailable()).thenReturn(null);

        new ActivityLifecycleScheduler(repository, cacheManager, indexProvider).autoEndActivities();

        assertThat(activity.getStatus()).isEqualTo("ended");
        verify(repository).saveAllAndFlush(List.of(activity));
        verify(detail).evict(8L);
        verify(analytics).evict(8L);
        verify(hotList).clear();
    }
}
