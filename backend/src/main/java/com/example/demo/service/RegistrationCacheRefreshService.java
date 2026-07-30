package com.example.demo.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.demo.config.RegistrationExecutorConfig;
import com.example.demo.recommend.UserPreferenceVectorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationCacheRefreshService {

    private final ActivityHotnessService activityHotnessService;
    private final ObjectProvider<UserPreferenceVectorService> userPreferenceVectorService;

    @Async(RegistrationExecutorConfig.CACHE_EXECUTOR)
    public void refresh(Long activityId, double hotnessScore, String userId) {
        try {
            activityHotnessService.cacheScore(activityId, hotnessScore);
            userPreferenceVectorService.ifAvailable(service -> service.invalidate(userId));
        }
        catch (RuntimeException ex) {
            // Registration data has already committed; cache refresh is
            // best-effort and the scheduled hotness job can repair it later.
            log.warn("Post-commit signup cache refresh failed: activityId={}, userId={}",
                    activityId, userId, ex);
        }
    }
}
