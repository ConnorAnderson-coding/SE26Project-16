package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.CacheNames;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.request.RegistrationRequest;
import com.example.demo.dto.response.RegistrationResponse;
import com.example.demo.entity.Activity;
import com.example.demo.entity.Registration;
import com.example.demo.entity.User;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final ActivityRepository activityRepository;
    private final UserService userService;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.ACTIVITY_DETAIL, key = "#request.activityId"),
            @CacheEvict(value = CacheNames.ACTIVITY_HOT_LIST, allEntries = true)
    })
    public RegistrationResponse signup(RegistrationRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        Activity activity = activityRepository.findById(request.getActivityId())
                .orElseThrow(() -> new BusinessException("活动不存在"));
        if (!"published".equals(activity.getStatus())) {
            throw new BusinessException("该活动暂不可报名");
        }
        if (registrationRepository.existsByActivityIdAndUserId(activity.getId(), userId)) {
            throw new BusinessException("您已报名该活动");
        }
        if (activity.getSignupCount() >= activity.getMaxParticipants()) {
            throw new BusinessException("报名人数已满");
        }

        User user = userService.getUserEntity(userId);
        Registration registration = new Registration();
        registration.setActivity(activity);
        registration.setUser(user);
        registration.setStatus("pending");
        registration.setCreatedAt(LocalDateTime.now());
        registrationRepository.save(registration);

        activity.setSignupCount(activity.getSignupCount() + 1);
        activity.setUpdatedAt(LocalDateTime.now());
        activityRepository.save(activity);

        registration.setActivity(activity);
        registration.setUser(user);
        return DtoMapper.toRegistrationResponse(registration);
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> getMine() {
        String userId = SecurityUtils.getCurrentUserId();
        return registrationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DtoMapper::toRegistrationResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> listForOrganizer(Long activityId) {
        String userId = SecurityUtils.getCurrentUserId();
        return registrationRepository.findByOrganizer(userId, activityId).stream()
                .map(DtoMapper::toRegistrationResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public RegistrationResponse review(Long id, boolean approved) {
        Registration registration = registrationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("报名记录不存在"));
        Activity activity = registration.getActivity();
        String userId = SecurityUtils.getCurrentUserId();
        if (!activity.getOrganizer().getId().equals(userId)) {
            throw new BusinessException(403, "无权审核该报名");
        }
        registration.setStatus(approved ? "approved" : "rejected");
        registration.setActivity(activity);
        registration.setUser(registration.getUser());
        return DtoMapper.toRegistrationResponse(registration);
    }

    @Transactional(readOnly = true)
    public String getSignupStatus(Long activityId) {
        String userId = SecurityUtils.getCurrentUserId();
        return registrationRepository.findByActivityIdAndUserId(activityId, userId)
                .map(Registration::getStatus)
                .orElse(null);
    }
}
