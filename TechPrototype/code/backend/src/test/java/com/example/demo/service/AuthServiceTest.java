package com.example.demo.service;

import com.example.demo.dto.response.AuthResponse;
import com.example.demo.dto.response.JAccountTokenResponse;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JAccountAuthClient;
import com.example.demo.security.JAccountUserInfo;
import com.example.demo.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void loginWithJAccountShouldMapExistingLocalUserByCode() {
        UserRepository userRepository = mock(UserRepository.class);
        JAccountAuthClient jAccountAuthClient = mock(JAccountAuthClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
                "campus-activity-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
                86400000);
        AuthService authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtTokenProvider,
                mock(AuthenticationManager.class),
                jAccountAuthClient,
                redisProvider);

        User existing = new User();
        existing.setId("524030910001");
        existing.setPasswordHash(passwordEncoder.encode("123456"));
        existing.setName("旧姓名");
        existing.setRole("student");
        existing.setCollege("软件学院");
        existing.setGrade("2024级");
        existing.setInterests(List.of("AI"));
        existing.setAvailableTime(List.of("weekend"));
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());

        when(jAccountAuthClient.exchangeCode("auth-code"))
                .thenReturn(new JAccountTokenResponse("access", "refresh", null, "Bearer", 1800L));
        when(jAccountAuthClient.fetchProfile("access"))
                .thenReturn(new JAccountUserInfo("ja-sub", "张三", "524030910001", "student"));
        when(userRepository.findByJaccount("ja-sub")).thenReturn(Optional.empty());
        when(userRepository.findById("524030910001")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.loginWithJAccount("auth-code", "state", "state");

        assertNotNull(response.getToken());
        assertEquals("524030910001", response.getUser().getId());
        assertEquals("张三", response.getUser().getName());
        assertEquals("ja-sub", response.getUser().getJaccount());
        verify(userRepository).save(existing);
    }
}
