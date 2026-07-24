package com.example.demo.service;

import com.example.demo.common.CacheNames;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.ActivityViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ActivityViewService {

    private final ActivityViewRepository activityViewRepository;
    private final ActivityRepository activityRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Caching(evict = {
            @CacheEvict(value = CacheNames.ACTIVITY_DETAIL, key = "#activityId"),
            @CacheEvict(value = CacheNames.ANALYTICS_ACTIVITY, key = "#activityId")
    })
    public void recordUniqueView(Long activityId, String userId) {
        // 数据冻结：活动已结束后不再记录浏览，避免污染分析指标。
        Activity activity = activityRepository.findById(activityId).orElse(null);
        if (activity == null || "ended".equals(activity.getStatus())) {
            return;
        }
        int inserted = activityViewRepository.insertIfAbsent(
                activityId, userId, LocalDateTime.now());
        if (inserted > 0) {
            activityRepository.incrementViewCount(activityId);
        }
    }
}
