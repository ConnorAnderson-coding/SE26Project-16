package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.CacheNames;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.dto.response.FavoriteStatusResponse;
import com.example.demo.dto.response.FavoriteToggleResponse;
import com.example.demo.entity.Activity;
import com.example.demo.entity.Favorite;
import com.example.demo.entity.FavoriteId;
import com.example.demo.entity.User;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.FavoriteRepository;
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
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ActivityRepository activityRepository;
    private final UserService userService;
    private final ActivityHotnessService activityHotnessService;

    @Transactional(readOnly = true)
    public List<ActivityResponse> getMine() {
        String userId = SecurityUtils.getCurrentUserId();
        return favoriteRepository.findByIdUserIdOrderByCreatedAtDesc(userId).stream()
                .map(f -> DtoMapper.toActivityResponse(f.getActivity()))
                .collect(Collectors.toList());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.ACTIVITY_DETAIL, key = "#activityId"),
            @CacheEvict(value = CacheNames.ACTIVITY_HOT_LIST, allEntries = true)
    })
    public FavoriteToggleResponse toggle(Long activityId) {
        String userId = SecurityUtils.getCurrentUserId();
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        FavoriteId favoriteId = new FavoriteId(userId, activityId);
        boolean exists = favoriteRepository.existsById(favoriteId);
        if (exists) {
            favoriteRepository.deleteById(favoriteId);
            activity.setFavoriteCount(Math.max(0, activity.getFavoriteCount() - 1));
            activity.setUpdatedAt(LocalDateTime.now());
        activityRepository.save(activity);
        activityHotnessService.recalculate(activity);
        return FavoriteToggleResponse.builder().favorited(false).build();
        }
        User user = userService.getUserEntity(userId);
        Favorite favorite = new Favorite();
        favorite.setId(favoriteId);
        favorite.setUser(user);
        favorite.setActivity(activity);
        favorite.setCreatedAt(LocalDateTime.now());
        favoriteRepository.save(favorite);
        activity.setFavoriteCount(activity.getFavoriteCount() + 1);
        activity.setUpdatedAt(LocalDateTime.now());
        activityRepository.save(activity);
        activityHotnessService.recalculate(activity);
        return FavoriteToggleResponse.builder().favorited(true).build();
    }

    @Transactional(readOnly = true)
    public FavoriteStatusResponse getStatus(Long activityId) {
        String userId = SecurityUtils.getCurrentUserId();
        boolean favorited = favoriteRepository.existsByIdUserIdAndIdActivityId(userId, activityId);
        return FavoriteStatusResponse.builder().favorited(favorited).build();
    }
}
