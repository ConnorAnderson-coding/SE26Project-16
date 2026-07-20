package com.example.demo.service;

import com.example.demo.repository.ActivityRepository;
import com.example.demo.repository.ActivityViewRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityViewServiceTest {

    private final ActivityViewRepository viewRepository = mock(ActivityViewRepository.class);
    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final ActivityViewService service = new ActivityViewService(viewRepository, activityRepository);

    @Test
    void incrementsOnlyForFirstViewBySameUser() {
        when(viewRepository.insertIfAbsent(any(), any(), any(LocalDateTime.class))).thenReturn(1);

        service.recordUniqueView(3L, "T001");

        verify(activityRepository).incrementViewCount(3L);
    }

    @Test
    void doesNotIncrementForDuplicateView() {
        when(viewRepository.insertIfAbsent(any(), any(), any(LocalDateTime.class))).thenReturn(0);

        service.recordUniqueView(3L, "T001");

        verify(activityRepository, never()).incrementViewCount(any());
    }
}
