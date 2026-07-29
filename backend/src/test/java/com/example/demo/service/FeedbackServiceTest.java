package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.request.FeedbackRequest;
import com.example.demo.entity.Activity;
import com.example.demo.entity.Feedback;
import com.example.demo.entity.User;
import com.example.demo.repository.CheckInRepository;
import com.example.demo.repository.FeedbackRepository;
import com.example.demo.repository.RegistrationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FeedbackServiceTest extends ServiceTestSupport {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitsAndListsFeedback() {
        FeedbackRepository feedbacks = mock(FeedbackRepository.class);
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        CheckInRepository checkIns = mock(CheckInRepository.class);
        ActivityService activities = mock(ActivityService.class);
        UserService users = mock(UserService.class);
        FeedbackService service = new FeedbackService(feedbacks, registrations, checkIns, activities, users);
        User user = login("u1", "student");
        Activity activity = activity(1L, user("owner", "teacher"));
        FeedbackRequest request = new FeedbackRequest();
        request.setActivityId(1L);
        request.setRating(5);
        request.setContent("Great");
        when(activities.getActivityEntity(1L)).thenReturn(activity);
        when(registrations.existsByActivityIdAndUserId(1L, "u1")).thenReturn(true);
        when(checkIns.existsByActivityIdAndUserId(1L, "u1")).thenReturn(true);
        when(users.getUserEntity("u1")).thenReturn(user);
        when(feedbacks.save(any(Feedback.class))).thenAnswer(i -> {
            Feedback feedback = i.getArgument(0);
            feedback.setId(1L);
            return feedback;
        });

        assertEquals(5, service.submit(request).getRating());
        Feedback saved = new Feedback();
        saved.setId(1L);
        saved.setActivity(activity);
        saved.setUser(user);
        saved.setRating(4);
        saved.setContent("Good");
        saved.setCreatedAt(LocalDateTime.now());
        when(feedbacks.findByUserIdOrderByCreatedAtDesc("u1")).thenReturn(List.of(saved));
        when(feedbacks.findByActivityIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(saved));
        assertEquals(1, service.getMine().size());
        assertEquals(1, service.listByActivity(1L).size());
    }

    @Test
    void rejectsUsersWithoutRegistrationOrCheckIn() {
        FeedbackRepository feedbacks = mock(FeedbackRepository.class);
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        CheckInRepository checkIns = mock(CheckInRepository.class);
        ActivityService activities = mock(ActivityService.class);
        FeedbackService service = new FeedbackService(feedbacks, registrations, checkIns, activities, mock(UserService.class));
        login("u1", "student");
        Activity activity = activity(1L, user("owner", "teacher"));
        FeedbackRequest request = new FeedbackRequest();
        request.setActivityId(1L);
        when(activities.getActivityEntity(1L)).thenReturn(activity);
        when(registrations.existsByActivityIdAndUserId(1L, "u1")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.submit(request));

        when(registrations.existsByActivityIdAndUserId(1L, "u1")).thenReturn(true);
        when(checkIns.existsByActivityIdAndUserId(1L, "u1")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.submit(request));
    }
}
