package com.example.demo.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;

@Service
public class ActivityHotnessService {

    private static final String ACTIVITY_HOTNESS_ZSET_KEY = "activity:hotness";

    private final ActivityRepository activityRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public ActivityHotnessService(ActivityRepository activityRepository, StringRedisTemplate stringRedisTemplate) {
        this.activityRepository = activityRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Scheduled(cron = "*/5 * * * * ?") // Run every 5 seconds for testing purposes
    public void scheduleHotnessCalculation() {
        System.out.println("热度更新任务正在执行...");
        calculateAndSaveHotnessScores();
    }

    public void calculateAndSaveHotnessScores() {
        List<Activity> activities = activityRepository.findAll();
        for (Activity activity : activities) {
            double hotnessScore = calculateHotness(activity);
            activity.setHotnessScore(hotnessScore);
            activityRepository.save(activity);
            // Synchronize to Redis ZSET
            stringRedisTemplate.opsForZSet().add(ACTIVITY_HOTNESS_ZSET_KEY, String.valueOf(activity.getId()), hotnessScore);
        }
    }

    public double calculateHotness(Activity activity) {
        // Weights (can be configured in application.properties or constants)
        final double VIEW_WEIGHT = 0.01;
        final double SIGNUP_WEIGHT = 0.1;
        final double CHECKIN_WEIGHT = 0.2;
        final double FAVORITE_WEIGHT = 0.15;
        final double GRAVITY = 1.8; // Hacker News gravity factor

        double baseScore = (activity.getViewCount() * VIEW_WEIGHT) +
                           (activity.getSignupCount() * SIGNUP_WEIGHT) +
                           (activity.getCheckInCount() * CHECKIN_WEIGHT) +
                           (activity.getFavoriteCount() * FAVORITE_WEIGHT);

        // Time decay (Hacker News style: score = points / (time + 2)^gravity)
        // Time is in hours since creation
        long hoursSinceCreation = ChronoUnit.HOURS.between(activity.getCreatedAt(), LocalDateTime.now());
        if (hoursSinceCreation < 0) { // Handle future creation dates if any
            hoursSinceCreation = 0;
        }

        double timeDecayFactor = Math.pow((hoursSinceCreation + 2), GRAVITY);
        double decayedScore = baseScore / timeDecayFactor;

        // Activity status correction
        switch (activity.getStatus()) {
            case "UPCOMING":
                decayedScore *= 1.5; // Boost upcoming activities
                break;
            case "ONGOING":
                decayedScore *= 1.2; // Slightly boost ongoing activities
                break;
            case "ENDED":
                decayedScore *= 0.1; // Significantly reduce score for ended activities
                break;
            case "CANCELLED":
                decayedScore = 0.0; // Set to zero for cancelled activities
                break;
            default:
                // No change for other statuses
                break;
        }
        return decayedScore;
    }
}