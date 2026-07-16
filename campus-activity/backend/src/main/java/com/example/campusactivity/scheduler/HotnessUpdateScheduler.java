package com.example.campusactivity.scheduler;

import com.example.campusactivity.entity.Activity;
import com.example.campusactivity.repository.ActivityRepository;
import com.example.campusactivity.service.HotnessCalculationService;
import com.example.campusactivity.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class HotnessUpdateScheduler {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private HotnessCalculationService hotnessCalculationService;

    @Autowired
    private RedisService redisService;

    /**
     * Scheduled task to update activity hotness every hour.
     * Runs every 3600000 ms (1 hour).
     */
    @Scheduled(fixedRate = 5000)
    public void updateActivityHotness() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        // Fetch activities that need recalculation:
        // 1. Published within 7 days
        // 2. Currently in progress
        // 3. Modified in the last 1 hour (using lastModifiedAt)
        // Note: activityRepository might need custom queries for these filters.
        // For now, I'll fetch all and filter in memory, which is not ideal for large datasets,
        // but will work for initial implementation. Custom repository methods will be needed for optimization.

        // Placeholder for fetching relevant activities
        List<Activity> activitiesToRecalculate = activityRepository.findActivitiesToRecalculateHotness(
                sevenDaysAgo,
                oneHourAgo,
                "in_progress"
        );

        for (Activity activity : activitiesToRecalculate) {
            double newHotness = hotnessCalculationService.calculateHotness(activity);
            activity.setHotness(newHotness);
            activityRepository.save(activity); // Update hotness in MySQL
            redisService.addOrUpdateActivityHotness(activity.getId(), newHotness); // Update hotness in Redis
        }

        System.out.println("Activity hotness updated at: " + LocalDateTime.now());
    }
}
