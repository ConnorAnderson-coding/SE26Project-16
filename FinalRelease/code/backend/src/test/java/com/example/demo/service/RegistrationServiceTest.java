package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.request.RegistrationRequest;
import com.example.demo.entity.Activity;
import com.example.demo.entity.Registration;
import com.example.demo.entity.User;
import com.example.demo.recommend.UserPreferenceVectorService;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.RegistrationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationServiceTest extends ServiceTestSupport {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<UserPreferenceVectorService> provider() {
        return mock(ObjectProvider.class);
    }

    @Test
    void signsUpAndQueriesRegistrations() {
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        ActivityRepository activities = mock(ActivityRepository.class);
        UserService users = mock(UserService.class);
        ObjectProvider<UserPreferenceVectorService> provider = provider();
        RegistrationService service = new RegistrationService(
                registrations, activities, users, provider, mock(ActivityHotnessService.class),
                mock(RegistrationCacheRefreshService.class));
        User student = login("student", "student");
        Activity activity = activity(1L, user("owner", "teacher"));
        activity.setSignupCount(1);
        when(activities.findByIdForUpdate(1L)).thenReturn(Optional.of(activity));
        when(registrations.existsByActivityIdAndUserId(1L, "student")).thenReturn(false);
        when(users.getUserEntity("student")).thenReturn(student);
        RegistrationRequest request = new RegistrationRequest();
        request.setActivityId(1L);

        assertEquals("pending", service.signup(request).getStatus());
        assertEquals(2, activity.getSignupCount());
        verify(registrations).saveAndFlush(any(Registration.class));
        verify(activities).save(activity);

        Registration registration = registration(activity, student, "approved");
        when(registrations.findByUserIdOrderByCreatedAtDesc("student")).thenReturn(List.of(registration));
        when(registrations.findByOrganizer("student", 1L)).thenReturn(List.of(registration));
        when(registrations.findByActivityIdAndUserId(1L, "student")).thenReturn(Optional.of(registration));
        assertEquals(1, service.getMine().size());
        assertEquals(1, service.listForOrganizer(1L).size());
        assertEquals("approved", service.getSignupStatus(1L));
    }

    @Test
    void rejectsInvalidSignupStates() {
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        ActivityRepository activities = mock(ActivityRepository.class);
        RegistrationService service = new RegistrationService(
                registrations, activities, mock(UserService.class), provider(), mock(ActivityHotnessService.class),
                mock(RegistrationCacheRefreshService.class));
        login("student", "student");
        RegistrationRequest request = new RegistrationRequest();
        request.setActivityId(1L);
        when(activities.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.signup(request));

        Activity activity = activity(1L, user("owner", "teacher"));
        when(activities.findByIdForUpdate(1L)).thenReturn(Optional.of(activity));
        activity.setStatus("draft");
        assertThrows(BusinessException.class, () -> service.signup(request));
        activity.setStatus("published");
        when(registrations.existsByActivityIdAndUserId(1L, "student")).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.signup(request));
        when(registrations.existsByActivityIdAndUserId(1L, "student")).thenReturn(false);
        activity.setSignupCount(100);
        activity.setMaxParticipants(100);
        when(activities.findByIdForUpdate(1L)).thenReturn(Optional.of(activity));
        assertThrows(BusinessException.class, () -> service.signup(request));
    }

    @Test
    void reviewsApprovedAndRejectedRegistrations() {
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        RegistrationService service = new RegistrationService(
                registrations, mock(ActivityRepository.class), mock(UserService.class), provider(),
                mock(ActivityHotnessService.class), mock(RegistrationCacheRefreshService.class));
        User owner = login("owner", "teacher");
        User student = user("student", "student");
        Activity activity = activity(1L, owner);
        Registration registration = registration(activity, student, "pending");
        when(registrations.findById(1L)).thenReturn(Optional.of(registration));
        assertEquals("approved", service.review(1L, true).getStatus());
        assertEquals("rejected", service.review(1L, false).getStatus());

        login("other", "teacher");
        assertThrows(BusinessException.class, () -> service.review(1L, true));
        when(registrations.findById(2L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.review(2L, true));
    }

    private static Registration registration(Activity activity, User user, String status) {
        Registration registration = new Registration();
        registration.setId(1L);
        registration.setActivity(activity);
        registration.setUser(user);
        registration.setStatus(status);
        registration.setCreatedAt(LocalDateTime.now());
        return registration;
    }
}
