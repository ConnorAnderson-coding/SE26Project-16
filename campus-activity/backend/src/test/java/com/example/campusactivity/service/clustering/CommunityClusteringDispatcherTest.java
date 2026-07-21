package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityClusteringDispatcherTest {
    @Mock
    private ClusteringRunLifecycleService lifecycleService;
    @Mock
    private CommunityClusteringRunExecutor runExecutor;
    @Mock
    private ClusteringRunFailureService failureService;
    @Mock
    private CommunityClusteringStartupRecovery startupRecovery;
    @Mock
    private ThreadPoolTaskExecutor taskExecutor;

    private CommunityClusteringDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new CommunityClusteringDispatcher(
                lifecycleService,
                runExecutor,
                failureService,
                startupRecovery,
                taskExecutor
        );
        org.mockito.Mockito.lenient().when(startupRecovery.isComplete()).thenReturn(true);
    }

    @Test
    void noPendingRunIsANoOp() {
        when(lifecycleService.claimNextPending()).thenReturn(Optional.empty());

        dispatcher.dispatchNext();

        verifyNoInteractions(taskExecutor, runExecutor, failureService);
    }

    @Test
    void claimedRunIsSubmittedAndExecutedOnceAcrossRepeatedTicks() {
        when(lifecycleService.claimNextPending())
                .thenReturn(Optional.of(running()))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        dispatcher.dispatchNext();
        dispatcher.dispatchNext();

        verify(runExecutor, times(1)).executeClaimedRun("run-1");
        verify(taskExecutor, times(1)).execute(any(Runnable.class));
    }

    @Test
    void executorRejectionMarksFailedAndDoesNotEscapeScheduler() {
        when(lifecycleService.claimNextPending())
                .thenReturn(Optional.of(running()));
        doThrow(new TaskRejectedException("rejected"))
                .when(taskExecutor).execute(any(Runnable.class));

        assertThatCode(dispatcher::dispatchNext).doesNotThrowAnyException();

        verify(failureService).markFailed(
                "run-1",
                ClusteringRunFailureCode.DISPATCH_REJECTED
        );
        verify(runExecutor, never()).executeClaimedRun(any());
    }

    @Test
    void failedTickDoesNotPreventLaterPolling() {
        when(lifecycleService.claimNextPending())
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(Optional.empty());

        assertThatCode(dispatcher::dispatchNext).doesNotThrowAnyException();
        assertThatCode(dispatcher::dispatchNext).doesNotThrowAnyException();

        verify(lifecycleService, times(2)).claimNextPending();
    }

    @Test
    void schedulerWaitsUntilStartupRecoveryCompletes() {
        when(startupRecovery.isComplete()).thenReturn(false);

        dispatcher.dispatchNext();

        verifyNoInteractions(lifecycleService, taskExecutor, runExecutor, failureService);
    }

    @Test
    void shutdownStopsNewClaims() {
        dispatcher.stopDispatching();

        dispatcher.dispatchNext();

        verifyNoInteractions(lifecycleService, taskExecutor, runExecutor, failureService);
    }

    private static ClusteringRunSnapshot running() {
        return new ClusteringRunSnapshot(
                "run-1",
                "version-1",
                ClusteringAlgorithm.KMEANS,
                2,
                42,
                ClusteringRunStatus.RUNNING,
                2,
                "community-features-v1",
                "{}",
                null,
                Instant.parse("2026-07-21T00:00:01Z"),
                null,
                null,
                "admin-1",
                Instant.parse("2026-07-21T00:00:00Z")
        );
    }
}
