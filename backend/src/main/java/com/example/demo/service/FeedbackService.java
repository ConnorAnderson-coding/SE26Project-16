package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.CacheNames;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.request.FeedbackRequest;
import com.example.demo.dto.response.FeedbackResponse;
import com.example.demo.entity.Activity;
import com.example.demo.entity.Feedback;
import com.example.demo.entity.User;
import com.example.demo.repository.CheckInRepository;
import com.example.demo.repository.FeedbackRepository;
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
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final RegistrationRepository registrationRepository;
    private final CheckInRepository checkInRepository;
    private final ActivityService activityService;
    private final UserService userService;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.FEEDBACK_BY_ACTIVITY, key = "#request.activityId"),
            @CacheEvict(value = CacheNames.ANALYTICS_ACTIVITY, key = "#request.activityId")
    })
    public FeedbackResponse submit(FeedbackRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        Activity activity = activityService.getActivityEntity(request.getActivityId());

        if (!registrationRepository.existsByActivityIdAndUserId(activity.getId(), userId)) {
            throw new BusinessException("您尚未报名该活动，需要先报名并通过审核后才能提交评价");
        }
        if (!checkInRepository.existsByActivityIdAndUserId(activity.getId(), userId)) {
            throw new BusinessException("您尚未签到该活动，签到后方可提交评价");
        }

        User user = userService.getUserEntity(userId);

        Feedback feedback = new Feedback();
        feedback.setActivity(activity);
        feedback.setUser(user);
        feedback.setRating(request.getRating());
        feedback.setContent(request.getContent());
        feedback.setCreatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);
        return DtoMapper.toFeedbackResponse(feedback);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getMine() {
        String userId = SecurityUtils.getCurrentUserId();
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DtoMapper::toFeedbackResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> listByActivity(Long activityId) {
        return feedbackRepository.findByActivityIdOrderByCreatedAtDesc(activityId).stream()
                .map(DtoMapper::toFeedbackResponse)
                .collect(Collectors.toList());
    }
}
