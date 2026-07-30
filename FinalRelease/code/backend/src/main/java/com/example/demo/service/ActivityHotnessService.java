package com.example.demo.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityHotnessService implements ApplicationRunner {

    static final String ACTIVITY_HOTNESS_ZSET_KEY = "activity:hotness";

    private static final double WEIGHT_VIEWS = 0.15;
    private static final double WEIGHT_SIGNUPS = 0.35;
    private static final double WEIGHT_CHECKINS = 0.30;
    private static final double WEIGHT_FAVORITES = 0.20;

    private static final double DECAY_FACTOR_12H = 0.99;
    private static final double DECAY_FACTOR_72H = 0.95;
    /** 72 小时衰减系数（之后趋于平稳，不再继续指数衰减） */
    private static final double DECAY_MULTIPLIER_AT_72H =
            Math.pow(DECAY_FACTOR_12H, 12) * Math.pow(DECAY_FACTOR_72H, 60);

    private static final double MULTIPLIER_NOT_STARTED_GT_48H = 0.8;
    private static final double MULTIPLIER_NOT_STARTED_LE_48H = 1.3;
    private static final double MULTIPLIER_IN_PROGRESS = 1.5;
    private static final double MULTIPLIER_ENDED_LE_7D = 0.7;
    private static final double MULTIPLIER_ENDED_GT_7D = 0.3;

    private final ActivityRepository activityRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("初始化活动综合热度分...");
        int updated = calculateAndSaveHotnessScores();
        log.info("活动综合热度分初始化完成，共 {} 条", updated);
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void scheduleHotnessCalculation() {
        log.debug("定时更新活动综合热度分");
        calculateAndSaveHotnessScores();
    }

    @Transactional
    public int calculateAndSaveHotnessScores() {
        List<Activity> activities = activityRepository.findAll();
        for (Activity activity : activities) {
            persistHotness(activity);
        }
        return activities.size();
    }

    @Transactional
    public void recalculate(Activity activity) {
        if (activity == null || activity.getId() == null) {
            return;
        }
        persistHotness(activity);
    }

    @Transactional
    public void recalculateById(Long activityId) {
        if (activityId == null) {
            return;
        }
        activityRepository.findById(activityId).ifPresent(this::persistHotness);
    }

    public double calculateHotness(Activity activity) {
        if (activity == null || activity.getCreatedAt() == null) {
            return 0.0;
        }
        if ("cancelled".equalsIgnoreCase(activity.getStatus())) {
            return 0.0;
        }

        double baseHotness = calculateBaseHotness(activity);
        double timeDecayedHotness = applyTimeDecay(baseHotness, activity.getCreatedAt());
        return applyStatusMultiplier(timeDecayedHotness, activity);
    }

    private void persistHotness(Activity activity) {
        double hotnessScore = calculateHotness(activity);
        activity.setHotnessScore(hotnessScore);
        activityRepository.save(activity);
        cacheScore(activity.getId(), hotnessScore);
    }

    public void cacheScore(Long activityId, double hotnessScore) {
        if (activityId == null) {
            return;
        }
        stringRedisTemplate.opsForZSet().add(
                ACTIVITY_HOTNESS_ZSET_KEY, String.valueOf(activityId), hotnessScore);
    }

    private double calculateBaseHotness(Activity activity) {
        Integer viewCount = activity.getViewCount();
        Integer signupCount = activity.getSignupCount();
        Integer checkInCount = activity.getCheckInCount();
        Integer favoriteCount = activity.getFavoriteCount();

        int views = viewCount != null ? viewCount : 0;
        int signups = signupCount != null ? signupCount : 0;
        int checkIns = checkInCount != null ? checkInCount : 0;
        int favorites = favoriteCount != null ? favoriteCount : 0;

        return views * WEIGHT_VIEWS
                + signups * WEIGHT_SIGNUPS
                + checkIns * WEIGHT_CHECKINS
                + favorites * WEIGHT_FAVORITES;
    }

    private double applyTimeDecay(double hotness, LocalDateTime createdAt) {
        long hoursSinceCreation = ChronoUnit.HOURS.between(createdAt, LocalDateTime.now());
        if (hoursSinceCreation < 0) {
            hoursSinceCreation = 0;
        }

        double decayMultiplier;
        if (hoursSinceCreation <= 12) {
            decayMultiplier = Math.pow(DECAY_FACTOR_12H, hoursSinceCreation);
        }
        else if (hoursSinceCreation <= 72) {
            decayMultiplier = Math.pow(DECAY_FACTOR_12H, 12)
                    * Math.pow(DECAY_FACTOR_72H, hoursSinceCreation - 12);
        }
        else {
            // 文档约定：72h 后趋于平稳并保留剩余热度，避免 seed/历史活动被压成 ~0
            decayMultiplier = DECAY_MULTIPLIER_AT_72H;
        }
        return hotness * decayMultiplier;
    }

    private double applyStatusMultiplier(double hotness, Activity activity) {
        LocalDateTime now = LocalDateTime.now();
        String status = activity.getStatus();
        if (status == null) {
            return hotness;
        }

        double multiplier = 1.0;
        switch (status.toLowerCase()) {
            case "published", "upcoming" -> {
                LocalDateTime startTime = activity.getStartTime();
                if (startTime != null) {
                    long hoursUntilStart = ChronoUnit.HOURS.between(now, startTime);
                    if (hoursUntilStart > 48) {
                        multiplier = MULTIPLIER_NOT_STARTED_GT_48H;
                    }
                    else if (hoursUntilStart > 0) {
                        multiplier = MULTIPLIER_NOT_STARTED_LE_48H;
                    }
                    else if (activity.getEndTime() != null && now.isBefore(activity.getEndTime())) {
                        multiplier = MULTIPLIER_IN_PROGRESS;
                    }
                }
            }
            case "in_progress", "ongoing" -> multiplier = MULTIPLIER_IN_PROGRESS;
            case "ended" -> {
                LocalDateTime endTime = activity.getEndTime();
                if (endTime != null) {
                    long daysSinceEnd = ChronoUnit.DAYS.between(endTime, now);
                    multiplier = daysSinceEnd <= 7
                            ? MULTIPLIER_ENDED_LE_7D
                            : MULTIPLIER_ENDED_GT_7D;
                }
            }
            default -> {
                // draft / unknown: no extra multiplier
            }
        }
        return hotness * multiplier;
    }
}
