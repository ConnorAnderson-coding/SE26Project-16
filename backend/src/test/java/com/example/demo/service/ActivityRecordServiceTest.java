package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.request.ActivityRecordRequest;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityRecord;
import com.example.demo.entity.User;
import com.example.demo.repository.ActivityRecordRepository;
import com.example.demo.repository.ActivityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActivityRecordServiceTest extends ServiceTestSupport {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getsAndPublishesRecord() {
        ActivityRecordRepository records = mock(ActivityRecordRepository.class);
        ActivityRepository activities = mock(ActivityRepository.class);
        ActivityService activityService = mock(ActivityService.class);
        ActivityHotnessService hotness = mock(ActivityHotnessService.class);
        ActivityRecordService service = new ActivityRecordService(records, activities, activityService, hotness);
        User owner = login("owner", "teacher");
        Activity activity = activity(1L, owner);
        ActivityRecord existing = new ActivityRecord();
        existing.setActivity(activity);
        existing.setSummary("Existing");
        existing.setPhotos(List.of());
        existing.setPublishedAt(LocalDateTime.now());
        when(records.findById(1L)).thenReturn(Optional.of(existing));
        assertEquals("Existing", service.getByActivityId(1L).getSummary());

        when(activityService.getActivityEntity(1L)).thenReturn(activity);
        when(records.existsById(1L)).thenReturn(false);
        ActivityRecordRequest request = new ActivityRecordRequest();
        request.setSummary("Finished");
        request.setPhotos(null);
        assertEquals("Finished", service.publish(1L, request).getSummary());
        assertEquals("ended", activity.getStatus());
        verify(records).save(any(ActivityRecord.class));
        verify(activities).save(activity);
        verify(hotness).recalculate(activity);
    }

    @Test
    void rejectsMissingDuplicateAndUnauthorizedRecords() {
        ActivityRecordRepository records = mock(ActivityRecordRepository.class);
        ActivityService activityService = mock(ActivityService.class);
        ActivityRecordService service = new ActivityRecordService(
                records, mock(ActivityRepository.class), activityService, mock(ActivityHotnessService.class));
        when(records.findById(1L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.getByActivityId(1L));

        login("other", "student");
        Activity activity = activity(1L, user("owner", "teacher"));
        when(activityService.getActivityEntity(1L)).thenReturn(activity);
        assertThrows(BusinessException.class, () -> service.publish(1L, new ActivityRecordRequest()));

        login("owner", "teacher");
        when(records.existsById(1L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.publish(1L, new ActivityRecordRequest()));
    }
}
