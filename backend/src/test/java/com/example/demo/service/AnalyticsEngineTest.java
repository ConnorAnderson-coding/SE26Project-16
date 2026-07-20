package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.CheckInRepository;
import com.example.demo.repository.FeedbackRepository;
import com.example.demo.repository.RegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsEngineTest {

    @Mock ActivityRepository activityRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock CheckInRepository checkInRepository;
    @Mock FeedbackRepository feedbackRepository;
    @InjectMocks AnalyticsEngine analyticsEngine;

    @Test
    void metricsUseActivityAggregateColumnsLikeDetailPage() {
        Activity activity = new Activity();
        activity.setId(7L);
        activity.setTitle("test");
        activity.setViewCount(120);
        activity.setSignupCount(31);
        activity.setFavoriteCount(19);
        activity.setMaxParticipants(40);
        activity.setStartTime(LocalDateTime.now().minusDays(1));
        activity.setEndTime(LocalDateTime.now());

        when(activityRepository.findById(7L)).thenReturn(Optional.of(activity));
        when(registrationRepository.countByActivityIdAndStatus(7L, "approved")).thenReturn(3L);
        when(registrationRepository.countDailySignupsByActivityId(7L)).thenReturn(List.of());
        when(checkInRepository.countByActivityId(7L)).thenReturn(1L);
        when(checkInRepository.countByMethodGroupByActivityId(7L)).thenReturn(List.of());
        when(feedbackRepository.findByActivityIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());

        ActivityMetrics metrics = analyticsEngine.computeMetrics(7L);

        // 与详情页同源：activity 表冗余字段，而非 registration/favorite 明细行数
        assertThat(metrics.getSignupCount()).isEqualTo(31);
        assertThat(metrics.getViewCount()).isEqualTo(120);
        assertThat(metrics.getFavoriteCount()).isEqualTo(19);
        assertThat(metrics.getApprovedCount()).isEqualTo(3L);
        assertThat(metrics.getCheckInCount()).isEqualTo(1L);
        // 报名转化率 = 31/120 * 100
        assertThat(metrics.getSignupRate()).isEqualByComparingTo("25.8");
        // 到场率 = 1/31 * 100
        assertThat(metrics.getAttendanceRate()).isEqualByComparingTo("3.2");
    }
}
