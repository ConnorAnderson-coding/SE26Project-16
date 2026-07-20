package com.example.demo.service;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.ActivityViewRepository;
import com.example.demo.repository.CheckInRepository;
import com.example.demo.repository.FavoriteRepository;
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
    @Mock ActivityViewRepository activityViewRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock CheckInRepository checkInRepository;
    @Mock FeedbackRepository feedbackRepository;
    @Mock FavoriteRepository favoriteRepository;
    @Mock ActivityAnalysisRepository analysisRepository;
    @InjectMocks AnalyticsEngine analyticsEngine;

    @Test
    void snapshotUsesFavoriteDetailsInsteadOfAggregateColumn() {
        Activity activity = new Activity();
        activity.setId(7L);
        activity.setTitle("test");
        activity.setFavoriteCount(99);
        activity.setStartTime(LocalDateTime.now().minusDays(1));
        activity.setEndTime(LocalDateTime.now());

        when(activityRepository.findById(7L)).thenReturn(Optional.of(activity));
        when(analysisRepository.findByActivityId(7L)).thenReturn(Optional.empty());
        when(activityViewRepository.countByActivityId(7L)).thenReturn(10L);
        when(registrationRepository.countByActivityId(7L)).thenReturn(4L);
        when(registrationRepository.countByActivityIdAndStatus(7L, "approved")).thenReturn(3L);
        when(registrationRepository.countDailySignupsByActivityId(7L)).thenReturn(List.of());
        when(favoriteRepository.countByIdActivityId(7L)).thenReturn(2L);
        when(checkInRepository.countByActivityId(7L)).thenReturn(1L);
        when(checkInRepository.countByMethodGroupByActivityId(7L)).thenReturn(List.of());
        when(feedbackRepository.findByActivityIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());

        ActivityMetrics metrics = analyticsEngine.computeMetrics(7L);

        assertThat(metrics.getFavoriteCount()).isEqualTo(2);
        assertThat(metrics.getViewCount()).isEqualTo(10);
        assertThat(metrics.getSignupCount()).isEqualTo(4);
    }
}
