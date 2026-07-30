package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.request.UpdateProfileRequest;
import com.example.demo.entity.User;
import com.example.demo.recommend.UserPreferenceVectorService;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest extends ServiceTestSupport {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getsCurrentAndEntityAndReportsMissingUser() {
        UserRepository repository = mock(UserRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserPreferenceVectorService> provider = mock(ObjectProvider.class);
        UserService service = new UserService(repository, provider);
        User user = login("u1", "student");
        when(repository.findCachedById("u1")).thenReturn(Optional.of(user));

        assertEquals("u1", service.getCurrentUser().getId());
        assertSame(user, service.getUserEntity("u1"));

        when(repository.findCachedById("u1")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, service::getCurrentUser);
        assertThrows(BusinessException.class, () -> service.getUserEntity("u1"));
    }

    @Test
    void updatesProfileAndInvalidatesPreferenceVector() {
        UserRepository repository = mock(UserRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserPreferenceVectorService> provider = mock(ObjectProvider.class);
        UserPreferenceVectorService vectors = mock(UserPreferenceVectorService.class);
        doAnswer(invocation -> {
            java.util.function.Consumer<UserPreferenceVectorService> consumer = invocation.getArgument(0);
            consumer.accept(vectors);
            return null;
        }).when(provider).ifAvailable(any());
        UserService service = new UserService(repository, provider);
        User user = login("u1", "student");
        when(repository.findById("u1")).thenReturn(Optional.of(user));
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setName("Updated");
        request.setCollege("Computer");
        request.setGrade("2025");
        request.setInterests(List.of("AI"));
        request.setAvailableTime(null);

        assertEquals("Updated", service.updateProfile(request).getName());
        assertEquals(List.of(), user.getAvailableTime());
        verify(repository).save(user);
        verify(vectors).invalidate("u1");
    }

    @Test
    void updateReportsMissingUser() {
        UserRepository repository = mock(UserRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserPreferenceVectorService> provider = mock(ObjectProvider.class);
        UserService service = new UserService(repository, provider);
        login("u1", "student");
        when(repository.findById("u1")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.updateProfile(new UpdateProfileRequest()));
    }

    @Test
    void updateAcceptsNullCollections() {
        UserRepository repository = mock(UserRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserPreferenceVectorService> provider = mock(ObjectProvider.class);
        UserService service = new UserService(repository, provider);
        User user = login("u1", "student");
        when(repository.findById("u1")).thenReturn(Optional.of(user));
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setName("Updated");
        request.setCollege("Computer");
        request.setGrade("2025");
        request.setInterests(null);
        request.setAvailableTime(List.of("weekend"));

        service.updateProfile(request);

        assertEquals(List.of(), user.getInterests());
        assertEquals(List.of("weekend"), user.getAvailableTime());
    }
}
