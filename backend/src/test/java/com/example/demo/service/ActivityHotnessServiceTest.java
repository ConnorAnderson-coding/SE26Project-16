package com.example.demo.service;

import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivityHotnessServiceTest extends ServiceTestSupport {

    @Test
    void calculatesBaseDecayAndEveryStatusMultiplier() {
        ActivityHotnessService service = service(mock(ActivityRepository.class));
        Activity activity = activity(1L, user("owner", "teacher"));
        activity.setViewCount(10);
        activity.setSignupCount(10);
        activity.setCheckInCount(10);
        activity.setFavoriteCount(10);

        activity.setCreatedAt(LocalDateTime.now());
        activity.setStartTime(LocalDateTime.now().plusHours(72));
        assertTrue(service.calculateHotness(activity) > 0);
        activity.setStartTime(LocalDateTime.now().plusHours(2));
        assertTrue(service.calculateHotness(activity) > 0);
        activity.setStartTime(LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        assertTrue(service.calculateHotness(activity) > 0);
        activity.setStatus("ongoing");
        assertTrue(service.calculateHotness(activity) > 0);
        activity.setStatus("ended");
        activity.setEndTime(LocalDateTime.now().minusDays(2));
        assertTrue(service.calculateHotness(activity) > 0);
        activity.setEndTime(LocalDateTime.now().minusDays(10));
        assertTrue(service.calculateHotness(activity) > 0);
        activity.setStatus("draft");
        activity.setCreatedAt(LocalDateTime.now().minusHours(30));
        assertTrue(service.calculateHotness(activity) > 0);
        activity.setCreatedAt(LocalDateTime.now().minusHours(100));
        assertTrue(service.calculateHotness(activity) > 0);
        activity.setCreatedAt(LocalDateTime.now().plusHours(1));
        assertTrue(service.calculateHotness(activity) > 0);
        activity.setStatus("cancelled");
        assertEquals(0.0, service.calculateHotness(activity));
        assertEquals(0.0, service.calculateHotness(null));
        activity.setCreatedAt(null);
        assertEquals(0.0, service.calculateHotness(activity));
    }

    @Test
    void persistsBulkScheduledStartupAndTargetedScores() {
        ActivityRepository repository = mock(ActivityRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        ActivityHotnessService service = new ActivityHotnessService(repository, redis);
        Activity activity = activity(1L, user("owner", "teacher"));
        when(repository.findAll()).thenReturn(List.of(activity));
        when(repository.findById(1L)).thenReturn(Optional.of(activity));

        service.calculateAndSaveHotnessScores();
        service.recalculate(activity);
        service.recalculateById(1L);
        service.run(null);
        service.scheduleHotnessCalculation();
        verify(repository, atLeast(5)).save(activity);
        verify(zset, atLeastOnce()).add(eq("activity:hotness"), eq("1"), anyDouble());

        service.recalculate(null);
        service.recalculate(new Activity());
        service.recalculateById(null);
    }

    private static ActivityHotnessService service(ActivityRepository repository) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        return new ActivityHotnessService(repository, redis);
    }
}
