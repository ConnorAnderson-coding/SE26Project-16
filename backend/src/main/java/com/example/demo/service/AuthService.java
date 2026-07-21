package com.example.demo.service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.dto.response.JAccountTokenResponse;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JAccountAuthClient;
import com.example.demo.security.JAccountUserInfo;
import com.example.demo.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final JAccountAuthClient jAccountAuthClient;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUserId(), request.getPassword()));
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        String token = jwtTokenProvider.generateToken(user.getId());
        return AuthResponse.builder()
                .token(token)
                .user(DtoMapper.toUserResponse(user))
                .build();
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsById(request.getId())) {
            throw new BusinessException("该学号/工号已注册");
        }
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setId(request.getId());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setRole(request.getRole() != null ? request.getRole() : "student");
        user.setCollege(request.getCollege());
        user.setGrade(request.getGrade());
        user.setInterests(request.getInterests() != null ? request.getInterests() : new ArrayList<>());
        user.setAvailableTime(request.getAvailableTime() != null ? request.getAvailableTime() : new ArrayList<>());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        String token = jwtTokenProvider.generateToken(user.getId());
        return AuthResponse.builder()
                .token(token)
                .user(DtoMapper.toUserResponse(user))
                .build();
    }

    public URI buildJAccountAuthorizationUri(String state) {
        return jAccountAuthClient.buildAuthorizationUri(state);
    }

    public URI buildJAccountLogoutUri(String state) {
        return jAccountAuthClient.buildLogoutUri(state);
    }

    @Transactional
    public AuthResponse loginWithJAccount(String code, String state, String savedState) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("jAccount 回调缺少授权码");
        }
        if (!StringUtils.hasText(state) || !Objects.equals(state, savedState)) {
            throw new BusinessException("jAccount 登录状态校验失败");
        }

        log.info("jAccount login state validated, exchanging authorization code");
        JAccountTokenResponse tokenResponse = jAccountAuthClient.exchangeCode(code);
        log.info("jAccount access token obtained, fetching profile");
        JAccountUserInfo userInfo = jAccountAuthClient.fetchProfile(tokenResponse.accessToken());
        log.info("jAccount profile resolved: subLength={}, code={}, type={}",
                userInfo.sub() == null ? -1 : userInfo.sub().length(), userInfo.code(), userInfo.type());
        User user = findOrCreateJAccountUser(userInfo);
        storeJAccountTokens(user.getId(), tokenResponse);

        String token = jwtTokenProvider.generateToken(user.getId());
        return AuthResponse.builder()
                .token(token)
                .user(DtoMapper.toUserResponse(user))
                .build();
    }

    private User findOrCreateJAccountUser(JAccountUserInfo userInfo) {
        return userRepository.findByJaccount(userInfo.sub())
                .map(user -> {
                    log.info("jAccount matched existing user by jaccount: userId={}", user.getId());
                    return updateJAccountProfile(user, userInfo);
                })
                .orElseGet(() -> mapOrCreateJAccountUser(userInfo));
    }

    private User mapOrCreateJAccountUser(JAccountUserInfo userInfo) {
        String userId = resolveLocalUserId(userInfo);
        return userRepository.findById(userId)
                .map(user -> {
                    if (StringUtils.hasText(user.getJaccount()) && !userInfo.sub().equals(user.getJaccount())) {
                        throw new BusinessException("该学号/工号已绑定其他 jAccount");
                    }
                    log.info("jAccount matched existing local user by code: userId={}", user.getId());
                    user.setJaccount(userInfo.sub());
                    return updateJAccountProfile(user, userInfo);
                })
                .orElseGet(() -> createJAccountUser(userId, userInfo));
    }

    private User createJAccountUser(String userId, JAccountUserInfo userInfo) {
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setId(userId);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setName(StringUtils.hasText(userInfo.name()) ? userInfo.name() : userInfo.sub());
        user.setRole(mapJAccountRole(userInfo.type()));
        user.setJaccount(userInfo.sub());
        user.setJaccountType(userInfo.type());
        user.setCollege("未设置");
        user.setGrade("未设置");
        user.setInterests(new ArrayList<>());
        user.setAvailableTime(new ArrayList<>());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        log.info("jAccount creating local user: userId={}, role={}, type={}",
                user.getId(), user.getRole(), user.getJaccountType());
        return userRepository.save(user);
    }

    private User updateJAccountProfile(User user, JAccountUserInfo userInfo) {
        if (StringUtils.hasText(userInfo.name())) {
            user.setName(userInfo.name());
        }
        user.setJaccount(userInfo.sub());
        user.setJaccountType(userInfo.type());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private String resolveLocalUserId(JAccountUserInfo userInfo) {
        String value = StringUtils.hasText(userInfo.code()) ? userInfo.code() : userInfo.sub();
        if (value.length() <= 32) {
            return value;
        }
        return value.substring(0, 32);
    }

    private String mapJAccountRole(String type) {
        if (type == null) {
            return "student";
        }
        return switch (type) {
            case "faculty", "external_teacher", "yxy", "fsyyjzg", "postphd" -> "teacher";
            default -> "student";
        };
    }

    private void storeJAccountTokens(String userId, JAccountTokenResponse tokenResponse) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        try {
            long expiresIn = tokenResponse.expiresIn();
            expiresIn = tokenResponse.expiresIn() != null ? expiresIn : 1800L;
            if (StringUtils.hasText(tokenResponse.accessToken())) {
                redisTemplate.opsForValue().set(
                        "campus:jaccount:access:" + userId,
                        tokenResponse.accessToken(),
                        Duration.ofSeconds(expiresIn));
            }
            if (StringUtils.hasText(tokenResponse.refreshToken())) {
                redisTemplate.opsForValue().set(
                        "campus:jaccount:refresh:" + userId,
                        tokenResponse.refreshToken(),
                        Duration.ofDays(30));
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to store jAccount tokens for user {}", userId, ex);
        }
    }
}
