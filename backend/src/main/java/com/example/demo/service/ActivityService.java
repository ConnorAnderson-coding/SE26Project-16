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
import com.example.demo.recommend.RecommendationScorer;
import com.example.demo.recommend.RecommendationService;
import com.example.demo.search.ActivityIndexService;
import com.example.demo.search.ActivitySearchCriteria;
import com.example.demo.search.SearchMode;
import com.example.demo.search.SearchSort;
import com.example.demo.search.service.ActivitySearchService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserService userService;
    private final RegistrationRepository registrationRepository;
    private final ObjectProvider<ActivityIndexService> activityIndexService;
    private final ObjectProvider<ActivitySearchService> activitySearchService;
    private final ObjectProvider<RecommendationService> recommendationService;

    @Transactional(readOnly = true)
    public PageResult<ActivityResponse> list(
            String category, String status, String location, String keyword,
            int page, int size, String sort, double matchWeight) {
        // keyword 非空且 ES 可用时走 Hybrid（BM25 + dense kNN）；索引为空则降级 MySQL，避免误显示 0 条
        if (StringUtils.hasText(keyword)) {
            ActivitySearchService searchService = activitySearchService.getIfAvailable();
            if (searchService != null && !searchService.isIndexEmpty()) {
                return searchService.search(new ActivitySearchCriteria(
                        keyword,
                        emptyToNull(category),
                        emptyToNull(status),
                        emptyToNull(location),
                        SearchMode.HYBRID,
                        SearchSort.from(sort),
                        matchWeight,
                        page,
                        size));
            }
            if (searchService != null) {
                log.warn("Elasticsearch 活动索引为空，关键词检索降级为 MySQL LIKE。请管理员执行 POST /api/v1/search/index/rebuild");
            }
        }

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

    @Transactional(readOnly = true)
    public ActivityResponse getById(Long id) {
        Activity activity = activityRepository.findWithDetailsById(id)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        return DtoMapper.toActivityResponse(activity);
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
        RecommendationService smart = recommendationService.getIfAvailable();
        if (smart != null) {
            try {
                return smart.recommend(limit);
            }
            catch (RuntimeException ex) {
                log.warn("智能推荐失败，降级为规则推荐: {}", ex.getMessage());
            }
        }
        return getRecommendedLegacy(limit);
    }

    /** Rule-based fallback when ES/recommend path unavailable. */
    @Transactional(readOnly = true)
    public List<ActivityResponse> getRecommendedLegacy(int limit) {
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
                    response.setRecommendReasons(legacyRecommendReasons(a, interests, score));
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
        syncSearchIndex(activity);
        return DtoMapper.toActivityResponse(activity);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.ACTIVITY_DETAIL, key = "#id"),
            @CacheEvict(value = CacheNames.ACTIVITY_HOT_LIST, allEntries = true)
    })
    public ActivityResponse update(Long id, ActivityRequest request) {
        Activity activity = getOwnedActivity(id);
        validateTimeRange(request.getStartTime(), request.getEndTime());
        applyRequest(activity, request);
        activity.setUpdatedAt(LocalDateTime.now());
        activityRepository.save(activity);
        syncSearchIndex(activity);
        return DtoMapper.toActivityResponse(activity);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.ACTIVITY_DETAIL, key = "#id"),
            @CacheEvict(value = CacheNames.ACTIVITY_HOT_LIST, allEntries = true),
            @CacheEvict(value = CacheNames.ACTIVITY_RECORD, key = "#id"),
            @CacheEvict(value = CacheNames.FEEDBACK_BY_ACTIVITY, key = "#id")
    })
    public void delete(Long id) {
        Activity activity = getOwnedActivity(id);
        activityRepository.delete(activity);
        activityIndexService.ifAvailable(service -> service.deleteActivity(id));
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

    private List<String> legacyRecommendReasons(Activity activity, List<String> interests, int score) {
        List<String> reasons = new ArrayList<>();
        List<String> tags = activity.getTags() != null ? activity.getTags() : List.of();
        boolean interestHit = tags.stream().anyMatch(interests::contains)
                || (activity.getCategory() != null && interests.contains(activity.getCategory()));
        if (interestHit) {
            reasons.add(RecommendationScorer.REASON_INTEREST);
        }
        if (activity.getSignupCount() + activity.getFavoriteCount() >= 40) {
            reasons.add(RecommendationScorer.REASON_HOT);
        }
        if (reasons.isEmpty()) {
            reasons.add(score > 0 ? RecommendationScorer.REASON_HOT : RecommendationScorer.REASON_COLD);
        }
        return reasons;
    }

    private Pageable buildPageable(int page, int size, String sort) {
        Sort defaultHot = Sort.by(Sort.Direction.DESC, "signupCount", "favoriteCount");
        if (!StringUtils.hasText(sort)) {
            return PageRequest.of(page, size, defaultHot);
        }

        // 前端检索排序（relevance/hot/composite 等）不是 Activity 实体字段；
        // ES 未启用或无 keyword 时走 MySQL，需映射到合法属性，避免 UnknownPathException。
        String key = sort.split(",")[0].trim().toLowerCase();
        return switch (key) {
            case "relevance", "composite", "hot" -> PageRequest.of(page, size, defaultHot);
            case "time" -> PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "startTime"));
            case "signup" -> PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "signupCount"));
            default -> {
                String[] parts = sort.split(",");
                Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                        ? Sort.Direction.ASC : Sort.Direction.DESC;
                yield PageRequest.of(page, size, Sort.by(direction, parts[0].trim()));
            }
        };
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    /** Persist/update ES doc (incl. search_text) and re-run embedding ingest pipeline. */
    private void syncSearchIndex(Activity activity) {
        activityIndexService.ifAvailable(service -> service.indexActivity(activity));
    }
}
