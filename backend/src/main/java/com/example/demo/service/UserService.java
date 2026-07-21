package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.CacheNames;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.request.UpdateProfileRequest;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.entity.User;
import com.example.demo.recommend.UserPreferenceVectorService;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ObjectProvider<UserPreferenceVectorService> userPreferenceVectorService;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findCachedById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return DtoMapper.toUserResponse(user);
    }

    @Transactional
    @CacheEvict(value = CacheNames.USER_PROFILE, key = "#result.id")
    public UserResponse updateProfile(UpdateProfileRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        user.setName(request.getName());
        user.setCollege(request.getCollege());
        user.setGrade(request.getGrade());
        user.setInterests(request.getInterests() != null ? request.getInterests() : new ArrayList<>());
        user.setAvailableTime(request.getAvailableTime() != null ? request.getAvailableTime() : new ArrayList<>());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        userPreferenceVectorService.ifAvailable(svc -> svc.invalidate(userId));
        return DtoMapper.toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public User getUserEntity(String userId) {
        return userRepository.findCachedById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }
}
