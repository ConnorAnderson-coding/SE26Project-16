package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.CacheNames;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.dto.response.FavoriteStatusResponse;
import com.example.demo.dto.response.FavoriteToggleResponse;
import com.example.demo.entity.Favorite;
import com.example.demo.entity.FavoriteId;
import com.example.demo.entity.User;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.FavoriteRepository;
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
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
            @CacheEvict(value = CacheNames.ACTIVITY_HOT_LIST, allEntries = true),
            @CacheEvict(value = CacheNames.ANALYTICS_ACTIVITY, key = "#activityId")
    })
    public FavoriteToggleResponse toggle(Long activityId) {
        String userId = SecurityUtils.getCurrentUserId();
        // Resolve user before locking the activity row to keep the critical section short.
        User user = userService.getUserEntity(userId);

        var activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        if ("ended".equals(activity.getStatus())) {
            throw new BusinessException("活动已结束，无法修改收藏状态");
        }

        FavoriteId favoriteId = new FavoriteId(userId, activityId);
        boolean exists = favoriteRepository.existsById(favoriteId);
        if (exists) {
            favoriteRepository.deleteById(favoriteId);
            activityRepository.decrementFavoriteCount(activityId, LocalDateTime.now());
            scheduleHotnessRecalculation(activityId);
            return FavoriteToggleResponse.builder().favorited(false).build();
        }

        Favorite favorite = new Favorite();
        favorite.setId(favoriteId);
        favorite.setUser(user);
        favorite.setActivity(activity);
        favorite.setCreatedAt(LocalDateTime.now());
        try {
            favoriteRepository.saveAndFlush(favorite);
        } catch (DataIntegrityViolationException ex) {
            return FavoriteToggleResponse.builder().favorited(true).build();
        }
        activityRepository.incrementFavoriteCount(activityId, LocalDateTime.now());
        scheduleHotnessRecalculation(activityId);
        return FavoriteToggleResponse.builder().favorited(true).build();
    }

    private void scheduleHotnessRecalculation(Long activityId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            activityHotnessService.recalculateById(activityId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    activityHotnessService.recalculateById(activityId);
                } catch (RuntimeException ex) {
                    log.warn("hotness recalculate after favorite failed activityId={}: {}",
                            activityId, ex.toString());
                }
            }
        });
    }

    @Transactional(readOnly = true)
    public FavoriteStatusResponse getStatus(Long activityId) {
        String userId = SecurityUtils.getCurrentUserId();
        boolean favorited = favoriteRepository.existsByIdUserIdAndIdActivityId(userId, activityId);
        return FavoriteStatusResponse.builder().favorited(favorited).build();
    }
}
