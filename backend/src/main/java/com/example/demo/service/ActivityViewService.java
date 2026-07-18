package com.example.demo.service;

import com.example.demo.common.CacheNames;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.ActivityViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityViewService {

    private final ActivityViewRepository activityViewRepository;
    private final ActivityRepository activityRepository;

    @Async
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.ACTIVITY_DETAIL, key = "#activityId"),
            @CacheEvict(value = CacheNames.ANALYTICS_ACTIVITY, key = "#activityId")
    })
    public void recordUniqueView(Long activityId, String userId) {
        int inserted = activityViewRepository.insertIfAbsent(
                activityId, userId, LocalDateTime.now());
        if (inserted == 0) {
            return;
        }
        activityRepository.incrementViewCount(activityId);
    }
}
