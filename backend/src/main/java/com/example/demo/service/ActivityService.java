package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.CacheNames;
import com.example.demo.common.PageResult;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.request.ActivityRequest;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.entity.Activity;
import com.example.demo.entity.User;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserService userService;
    private final RegistrationRepository registrationRepository;

    @Transactional(readOnly = true)
    public PageResult<ActivityResponse> list(
            String category, String status, String location, String keyword,
            int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<Activity> result = activityRepository.search(
                emptyToNull(category),
                emptyToNull(status),
                emptyToNull(location),
                emptyToNull(keyword),
                pageable);
        List<ActivityResponse> content = result.getContent().stream()
                .map(DtoMapper::toActivityResponse)
                .collect(Collectors.toList());
        return new PageResult<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /**
     * 查询活动详情并在事务内原子增加浏览量。
     * <p>
     * 已结束活动、组织者本人、管理员和未登录用户不会增加浏览量。
     */
    @Transactional
    public ActivityResponse getById(Long id) {
        Activity activity = activityRepository.findWithDetailsById(id)
                .orElseThrow(() -> new BusinessException("活动不存在"));

        if (shouldIncrementViewCount(activity)) {
            activityRepository.incrementViewCount(id);
        }

        return DtoMapper.toActivityResponse(activity);
    }

    /**
     * 判断本次浏览是否计入 viewCount。
     * <p>
     * 仅普通登录用户的"主动查看"计数：组织者本人、管理员、未登录用户均不计。
     * 活动一旦结束，浏览量即冻结不再变化。
     *
     * @param activity 当前活动实体（来自缓存）
     * @return true 表示需要自增 viewCount
     */
    private boolean shouldIncrementViewCount(Activity activity) {
        // 活动已结束：浏览量冻结
        if ("ended".equals(activity.getStatus())) {
            return false;
        }
        try {
            var currentUser = SecurityUtils.getCurrentUser();
            String currentUserId = currentUser.getUserId();
            if (activity.getOrganizerId() != null
                    && activity.getOrganizerId().equals(currentUserId)) {
                return false;
            }
            boolean isAdmin = currentUser.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            return !isAdmin;
        } catch (BusinessException e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getMine() {
        String userId = SecurityUtils.getCurrentUserId();
        return activityRepository.findByOrganizerIdOrderByStartTimeDesc(userId).stream()
                .map(DtoMapper::toActivityResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getRecommended(int limit) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserEntity(userId);
        List<String> interests = user.getInterests() != null ? user.getInterests() : List.of();

        List<Long> signedUpIds = registrationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> r.getActivity().getId())
                .collect(Collectors.toList());

        List<Activity> hotActivities = activityRepository.findPublishedByHot(PageRequest.of(0, 50));
        return hotActivities.stream()
                .filter(a -> !signedUpIds.contains(a.getId()))
                .map(a -> {
                    ActivityResponse response = DtoMapper.toActivityResponse(a);
                    int score = computeRecommendScore(a, interests);
                    response.setRecommendScore(score);
                    return response;
                })
                .sorted(Comparator.comparing(ActivityResponse::getRecommendScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = CacheNames.ACTIVITY_HOT_LIST, allEntries = true)
    public ActivityResponse create(ActivityRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        User organizer = userService.getUserEntity(userId);
        validateTimeRange(request.getStartTime(), request.getEndTime());

        LocalDateTime now = LocalDateTime.now();
        Activity activity = new Activity();
        applyRequest(activity, request);
        activity.setOrganizer(organizer);
        activity.setCollege(organizer.getCollege());
        activity.setSignupCount(0);
        activity.setFavoriteCount(0);
        activity.setStatus("published");
        activity.setCheckInCode("CK" + Long.toString(System.currentTimeMillis(), 36).toUpperCase().substring(Math.max(0, Long.toString(System.currentTimeMillis(), 36).length() - 4)));
        activity.setCreatedAt(now);
        activity.setUpdatedAt(now);
        activityRepository.save(activity);
        activity.setOrganizer(organizer);
        return DtoMapper.toActivityResponse(activity);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.ACTIVITY_DETAIL, key = "#id"),
            @CacheEvict(value = CacheNames.ACTIVITY_HOT_LIST, allEntries = true),
            @CacheEvict(value = CacheNames.ANALYTICS_ACTIVITY, key = "#id")
    })
    public ActivityResponse update(Long id, ActivityRequest request) {
        Activity activity = getOwnedActivity(id);
        validateTimeRange(request.getStartTime(), request.getEndTime());
        applyRequest(activity, request);
        activity.setUpdatedAt(LocalDateTime.now());
        activityRepository.save(activity);
        return DtoMapper.toActivityResponse(activity);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.ACTIVITY_DETAIL, key = "#id"),
            @CacheEvict(value = CacheNames.ACTIVITY_HOT_LIST, allEntries = true),
            @CacheEvict(value = CacheNames.ACTIVITY_RECORD, key = "#id"),
            @CacheEvict(value = CacheNames.FEEDBACK_BY_ACTIVITY, key = "#id"),
            @CacheEvict(value = CacheNames.ANALYTICS_ACTIVITY, key = "#id")
    })
    public void delete(Long id) {
        Activity activity = getOwnedActivity(id);
        activityRepository.delete(activity);
    }

    @Transactional(readOnly = true)
    public Activity getActivityEntity(Long id) {
        return activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("活动不存在"));
    }

    private Activity getOwnedActivity(Long id) {
        String userId = SecurityUtils.getCurrentUserId();
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        if (!activity.getOrganizer().getId().equals(userId)
                && !"admin".equals(SecurityUtils.getCurrentUser().getUser().getRole())) {
            throw new BusinessException(403, "无权操作该活动");
        }
        return activity;
    }

    private void applyRequest(Activity activity, ActivityRequest request) {
        activity.setTitle(request.getTitle());
        activity.setCategory(request.getCategory());
        activity.setDescription(request.getDescription());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setLocation(request.getLocation());
        activity.setMaxParticipants(request.getMaxParticipants());
        activity.setPoster(request.getPoster());
        activity.setTags(request.getTags() != null ? request.getTags() : new ArrayList<>());
    }

    private void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (end.isBefore(start)) {
            throw new BusinessException("结束时间不能早于开始时间");
        }
    }

    private int computeRecommendScore(Activity activity, List<String> interests) {
        int score = 0;
        List<String> tags = activity.getTags() != null ? activity.getTags() : List.of();
        long tagMatch = tags.stream().filter(interests::contains).count();
        score += (int) tagMatch * 30;
        score += Math.min(activity.getFavoriteCount(), 50);
        score += Math.min(activity.getSignupCount(), 30);
        return score;
    }

    private Pageable buildPageable(int page, int size, String sort) {
        if (!StringUtils.hasText(sort)) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "signupCount", "favoriteCount"));
        }
        String[] parts = sort.split(",");
        String property = parts[0];
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
