package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.CacheNames;
import com.example.demo.dto.DtoMapper;
import com.example.demo.dto.request.ActivityRecordRequest;
import com.example.demo.dto.response.ActivityRecordResponse;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityRecord;
import com.example.demo.repository.ActivityRecordRepository;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class ActivityRecordService {

    private final ActivityRecordRepository activityRecordRepository;
    private final ActivityRepository activityRepository;
    private final ActivityService activityService;

    @Transactional(readOnly = true)
    public ActivityRecordResponse getByActivityId(Long activityId) {
        ActivityRecord record = activityRecordRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException("活动记录不存在"));
        return DtoMapper.toRecordResponse(record);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.ACTIVITY_DETAIL, key = "#activityId"),
            @CacheEvict(value = CacheNames.ACTIVITY_RECORD, key = "#activityId"),
            @CacheEvict(value = CacheNames.ACTIVITY_HOT_LIST, allEntries = true),
            @CacheEvict(value = CacheNames.ANALYTICS_ACTIVITY, key = "#activityId")
    })
    public ActivityRecordResponse publish(Long activityId, ActivityRecordRequest request) {
        Activity activity = activityService.getActivityEntity(activityId);
        String userId = SecurityUtils.getCurrentUserId();
        if (!activity.getOrganizer().getId().equals(userId)) {
            throw new BusinessException(403, "无权发布该活动记录");
        }
        if (activityRecordRepository.existsById(activityId)) {
            throw new BusinessException("活动记录已发布");
        }

        ActivityRecord record = new ActivityRecord();
        record.setActivity(activity);
        record.setSummary(request.getSummary());
        record.setPhotos(request.getPhotos() != null ? request.getPhotos() : new ArrayList<>());
        record.setPublishedAt(LocalDateTime.now());
        activityRecordRepository.save(record);

        activity.setStatus("ended");
        activity.setUpdatedAt(LocalDateTime.now());
        activityRepository.save(activity);
        return DtoMapper.toRecordResponse(record);
    }
}
