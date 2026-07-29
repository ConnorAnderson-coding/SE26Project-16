package com.example.demo.service;

import com.example.demo.dto.response.HomeStatsResponse;
import com.example.demo.repository.FavoriteRepository;
import com.example.demo.repository.RegistrationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class HomeServiceTest extends ServiceTestSupport {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aggregatesCurrentUserStats() {
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        FavoriteRepository favorites = mock(FavoriteRepository.class);
        login("u1", "student");
        when(registrations.countByUserId("u1")).thenReturn(4L);
        when(registrations.countByUserIdAndStatus("u1", "approved")).thenReturn(3L);
        when(favorites.countByIdUserId("u1")).thenReturn(2L);

        HomeStatsResponse result = new HomeService(registrations, favorites).getStats();
        assertEquals(4L, result.getMySignupCount());
        assertEquals(3L, result.getApprovedCount());
        assertEquals(2L, result.getMyFavoriteCount());
    }
}
