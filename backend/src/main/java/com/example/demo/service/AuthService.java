package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUserId(), request.getPassword()));
        User user = userRepository.findByIdIgnoreCase(request.getUserId())
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
}
