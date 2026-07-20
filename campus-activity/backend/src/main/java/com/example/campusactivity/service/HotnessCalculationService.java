package com.example.campusactivity.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.campusactivity.entity.Activity;
import com.example.campusactivity.repository.ActivityRepository;
import com.example.campusactivity.repository.CheckInRepository;

@Service
public class HotnessCalculationService {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private CheckInRepository checkInRepository;

    // Weights for hotness factors
    private static final double WEIGHT_VIEWS = 0.15;
    private static final double WEIGHT_SIGNUPS = 0.35;
    private static final double WEIGHT_CHECKINS = 0.30;
    private static final double WEIGHT_FAVORITES = 0.20;

    // Time decay constants (example values, can be tuned)
    // Hacker News decay like: score = (P-1) / (T+2)^G, where P is points, T is time in hours, G is gravity
    // For simplicity, we are using a step-wise decay based on time intervals.
    // Decay is applied by multiplying current hotness by a decay factor.
    private static final double DECAY_FACTOR_12H = 0.99; // Slow decay per hour for 0-12 hours
    private static final double DECAY_FACTOR_72H = 0.95;  // Medium decay per hour for 12-72 hours (e.g., 0.99 * 0.95 per hour)
    private static final double DECAY_FACTOR_AFTER_72H = 0.90; // Faster decay per hour after 72 hours

    // Activity status multipliers
    private static final double MULTIPLIER_NOT_STARTED_GT_48H = 0.8; // > 48 hours before start
    private static final double MULTIPLIER_NOT_STARTED_LE_48H = 1.3; // <= 48 hours before start
    private static final double MULTIPLIER_IN_PROGRESS = 1.5;
    private static final double MULTIPLIER_ENDED_LE_7D = 0.7; // Ended <= 7 days ago
    private static final double MULTIPLIER_ENDED_GT_7D = 0.3;  // Ended > 7 days ago

    public double calculateHotness(Activity activity) {
        if (activity == null || activity.getCreatedAt() == null) {
            return 0.0;
        }

        double baseHotness = calculateBaseHotness(activity);
        double timeDecayedHotness = applyTimeDecay(baseHotness, activity.getCreatedAt());
        double finalHotness = applyStatusMultiplier(timeDecayedHotness, activity);

        return finalHotness;
    }

    private double calculateBaseHotness(Activity activity) {
        double views = activity.getViewCount() != null ? activity.getViewCount() : 0;
        double signups = activity.getSignupCount() != null ? activity.getSignupCount() : 0;
        double checkIns = checkInRepository.countByActivityId(activity.getId());
        double favorites = activity.getFavoriteCount() != null ? activity.getFavoriteCount() : 0;

        return (views * WEIGHT_VIEWS) +
               (signups * WEIGHT_SIGNUPS) +
               (checkIns * WEIGHT_CHECKINS) +
               (favorites * WEIGHT_FAVORITES);
    }

    private double applyTimeDecay(double hotness, LocalDateTime createdAt) {
        long hoursSinceCreation = ChronoUnit.HOURS.between(createdAt, LocalDateTime.now());

        double decayMultiplier = 1.0;
        if (hoursSinceCreation <= 12) {
            decayMultiplier = Math.pow(DECAY_FACTOR_12H, hoursSinceCreation);
        } else if (hoursSinceCreation <= 72) {
            decayMultiplier = Math.pow(DECAY_FACTOR_12H, 12) * Math.pow(DECAY_FACTOR_72H, hoursSinceCreation - 12);
        } else {
            decayMultiplier = Math.pow(DECAY_FACTOR_12H, 12) * Math.pow(DECAY_FACTOR_72H, 60) * Math.pow(DECAY_FACTOR_AFTER_72H, hoursSinceCreation - 72);
        }
        return hotness * decayMultiplier;
    }

    private double applyStatusMultiplier(double hotness, Activity activity) {
        LocalDateTime now = LocalDateTime.now();
        String status = activity.getStatus();
        LocalDateTime startTime = activity.getStartTime();
        LocalDateTime endTime = activity.getEndTime();

        if (status == null) {
            return hotness; 
        }

        double multiplier = 1.0;
        switch (status.toLowerCase()) {
            case "published": 
            case "upcoming": 
                if (startTime != null) {
                    long hoursUntilStart = ChronoUnit.HOURS.between(now, startTime);
                    if (hoursUntilStart > 48) {
                        multiplier = MULTIPLIER_NOT_STARTED_GT_48H;
                    } else if (hoursUntilStart > 0 && hoursUntilStart <= 48) {
                        multiplier = MULTIPLIER_NOT_STARTED_LE_48H;
                    }
                }
                break;
            case "in_progress": 
                multiplier = MULTIPLIER_IN_PROGRESS;
                break;
            case "ended": 
                if (endTime != null) {
                    long daysSinceEnd = ChronoUnit.DAYS.between(endTime, now);
                    if (daysSinceEnd <= 7) {
                        multiplier = MULTIPLIER_ENDED_LE_7D;
                    } else {
                        multiplier = MULTIPLIER_ENDED_GT_7D;
                    }
                }
                break;
            default:
                break;
        }
        return hotness * multiplier;
    }
}