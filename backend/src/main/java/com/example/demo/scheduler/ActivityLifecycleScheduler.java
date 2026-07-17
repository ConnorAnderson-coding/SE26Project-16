package com.example.demo.scheduler;

import com.example.demo.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLifecycleScheduler {

    private final ActivityRepository activityRepository;

    @Scheduled(cron = "0 30 0 * * ?")
    @Transactional
    public void autoEndActivities() {
        LocalDateTime now = LocalDateTime.now();
        int updated = activityRepository.markEndedBefore(now);
        if (updated > 0) {
            log.info("活动生命周期任务：将 {} 个已过结束时间的活动标记为 ended (截止 {})", updated, now);
        } else {
            log.debug("活动生命周期任务：无活动需要自动标记 (截止 {})", now);
        }
    }
}
