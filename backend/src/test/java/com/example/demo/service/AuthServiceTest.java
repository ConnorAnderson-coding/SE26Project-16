package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.dto.response.JAccountTokenResponse;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JAccountAuthClient;
import com.example.demo.security.JAccountUserInfo;
import com.example.demo.security.JwtTokenProvider;
import com.example.demo.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private JAccountAuthClient jAccountAuthClient;
    private ObjectProvider<StringRedisTemplate> redisProvider;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private AuthenticationManager authenticationManager;
    private AuthService authService;

    private static final String SECRET = "campus-activity-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm";
    private static final long EXPIRATION = 86400000;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jAccountAuthClient = mock(JAccountAuthClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        redisProvider = provider;
        passwordEncoder = new BCryptPasswordEncoder();
        jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION);
        authenticationManager = mock(AuthenticationManager.class);
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider,
                authenticationManager, jAccountAuthClient, redisProvider);
    }

    @Test
    void loginWithValidCredentialsReturnsToken() {
        User user = createUser("user1", "张三", "student");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(user), null, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        LoginRequest request = new LoginRequest();
        request.setUserId("user1");
        request.setPassword("password");

        AuthResponse response = authService.login(request);

        assertNotNull(response.getToken());
        assertEquals("user1", response.getUser().getId());
        assertEquals("张三", response.getUser().getName());
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("user1", "password"));
    }

    @Test
    void loginWithWrongPasswordThrowsBadCredentials() {
        doThrow(new BadCredentialsException("密码错误"))
                .when(authenticationManager).authenticate(any());

        LoginRequest request = new LoginRequest();
        request.setUserId("user1");
        request.setPassword("wrong");

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void loginWithNonExistentUserThrowsBadCredentials() {
        doThrow(new BadCredentialsException("用户不存在"))
                .when(authenticationManager).authenticate(any());

        LoginRequest request = new LoginRequest();
        request.setUserId("nonexistent");
        request.setPassword("password");

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void registerCreatesNewUserAndReturnsToken() {
        when(userRepository.existsById("newuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterRequest request = new RegisterRequest();
        request.setId("newuser");
        request.setPassword("password123");
        request.setName("新用户");
        request.setRole("student");
        request.setCollege("软件学院");
        request.setGrade("2024级");
        request.setInterests(List.of("AI", "体育"));
        request.setAvailableTime(List.of("weekend"));

        AuthResponse response = authService.register(request);

        assertNotNull(response.getToken());
        assertEquals("newuser", response.getUser().getId());
        assertEquals("新用户", response.getUser().getName());
        assertEquals("student", response.getUser().getRole());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUsesDefaultRoleWhenNull() {
        when(userRepository.existsById("newuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterRequest request = new RegisterRequest();
        request.setId("newuser");
        request.setPassword("password123");
        request.setName("新用户");

        AuthResponse response = authService.register(request);

        assertEquals("student", response.getUser().getRole());
    }

    @Test
    void registerUsesDefaultEmptyListsWhenNull() {
        when(userRepository.existsById("newuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterRequest request = new RegisterRequest();
        request.setId("newuser");
        request.setPassword("password123");
        request.setName("新用户");

        authService.register(request);

        verify(userRepository).save(argThat(user ->
                user.getInterests() != null && user.getInterests().isEmpty()
                && user.getAvailableTime() != null && user.getAvailableTime().isEmpty()));
    }

    @Test
    void registerThrowsWhenUserIdAlreadyExists() {
        when(userRepository.existsById("existing")).thenReturn(true);

        RegisterRequest request = new RegisterRequest();
        request.setId("existing");
        request.setPassword("password123");
        request.setName("已存在");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request));
        assertEquals("该学号/工号已注册", ex.getMessage());
    }

    @Test
    void loginWithJAccountCreatesNewLocalUser() {
        when(jAccountAuthClient.exchangeCode("auth-code"))
                .thenReturn(new JAccountTokenResponse("access-token", "refresh-token", null, "Bearer", 1800L));
        when(jAccountAuthClient.fetchProfile("access-token"))
                .thenReturn(new JAccountUserInfo("ja-sub", "张三", "524030910001", "student"));
        when(userRepository.findByJaccount("ja-sub")).thenReturn(Optional.empty());
        when(userRepository.findById("524030910001")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(redisProvider.getIfAvailable()).thenReturn(null);

        AuthResponse response = authService.loginWithJAccount("auth-code", "state", "state");

        assertNotNull(response.getToken());
        assertEquals("524030910001", response.getUser().getId());
        assertEquals("张三", response.getUser().getName());
        verify(userRepository).save(argThat(user ->
                "524030910001".equals(user.getId())
                && "张三".equals(user.getName())
                && "ja-sub".equals(user.getJaccount())
                && "student".equals(user.getRole())));
    }

    @Test
    void loginWithJAccountThrowsWhenCodeIsEmpty() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.loginWithJAccount(null, "state", "state"));
        assertEquals("jAccount 回调缺少授权码", ex.getMessage());

        ex = assertThrows(BusinessException.class,
                () -> authService.loginWithJAccount("", "state", "state"));
        assertEquals("jAccount 回调缺少授权码", ex.getMessage());
    }

    @Test
    void loginWithJAccountThrowsWhenStateMismatch() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.loginWithJAccount("code", "state1", "state2"));
        assertEquals("jAccount 登录状态校验失败", ex.getMessage());
    }

    @Test
    void loginWithJAccountThrowsWhenStateIsEmpty() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.loginWithJAccount("code", null, "state"));
        assertEquals("jAccount 登录状态校验失败", ex.getMessage());
    }

    @Test
    void loginWithJAccountMapsExistingUserByJaccount() {
        User existing = createUser("524030910001", "旧姓名", "student");
        when(jAccountAuthClient.exchangeCode("auth-code"))
                .thenReturn(new JAccountTokenResponse("access-token", "refresh-token", null, "Bearer", 1800L));
        when(jAccountAuthClient.fetchProfile("access-token"))
                .thenReturn(new JAccountUserInfo("ja-sub", "新姓名", "524030910001", "student"));
        when(userRepository.findByJaccount("ja-sub")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(redisProvider.getIfAvailable()).thenReturn(null);

        AuthResponse response = authService.loginWithJAccount("auth-code", "state", "state");

        assertEquals("524030910001", response.getUser().getId());
        assertEquals("新姓名", response.getUser().getName());
        assertEquals("ja-sub", existing.getJaccount());
        verify(userRepository).save(existing);
    }

    private static User createUser(String id, String name, String role) {
        User user = new User();
        user.setId(id);
        user.setPasswordHash("hash");
        user.setName(name);
        user.setRole(role);
        user.setCollege("软件学院");
        user.setGrade("2024级");
        user.setInterests(List.of("AI"));
        user.setAvailableTime(List.of("weekend"));
        user.setCreatedAt(LocalDateTime.now().minusDays(1));
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
