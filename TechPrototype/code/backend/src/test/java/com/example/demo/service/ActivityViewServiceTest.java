package com.example.demo.service;

import com.example.demo.entity.Activity;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.ActivityViewRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityViewServiceTest {

    private final ActivityViewRepository viewRepository = mock(ActivityViewRepository.class);
    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final ActivityViewService service = new ActivityViewService(viewRepository, activityRepository);

    /** 构造一个"进行中"的活动，供 recordUniqueView 的状态守卫放行。 */
    private void stubActiveActivity(Long id) {
        Activity a = new Activity();
        a.setId(id);
        a.setStatus("published");
        when(activityRepository.findById(id)).thenReturn(Optional.of(a));
    }

    @Test
    void incrementsOnlyForFirstViewBySameUser() {
        stubActiveActivity(3L);
        when(viewRepository.insertIfAbsent(any(), any(), any(LocalDateTime.class))).thenReturn(1);

        service.recordUniqueView(3L, "T001");

        verify(activityRepository).incrementViewCount(3L);
    }

    @Test
    void doesNotIncrementForDuplicateView() {
        stubActiveActivity(3L);
        when(viewRepository.insertIfAbsent(any(), any(), any(LocalDateTime.class))).thenReturn(0);

        service.recordUniqueView(3L, "T001");

        verify(activityRepository, never()).incrementViewCount(any());
    }

    /** 数据冻结：活动 status='ended' 后不再记录浏览/计数。 */
    @Test
    void doesNotRecordViewWhenActivityIsEnded() {
        Activity ended = new Activity();
        ended.setId(3L);
        ended.setStatus("ended");
        when(activityRepository.findById(3L)).thenReturn(Optional.of(ended));

        service.recordUniqueView(3L, "T001");

        verify(viewRepository, never()).insertIfAbsent(any(), any(), any(LocalDateTime.class));
        verify(activityRepository, never()).incrementViewCount(any());
    }
}
