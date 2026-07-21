package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityClusteringOrchestratorTest {
    @Mock
    private CommunityClusteringSubmissionService submissionService;
    @Mock
    private CommunityClusteringRunExecutor runExecutor;

    private CommunityClusteringOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new CommunityClusteringOrchestrator(
                submissionService,
                runExecutor
        );
    }

    @Test
    void nullCommandIsRejectedBeforeDependencies() {
        assertThatThrownBy(() -> orchestrator.execute(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(submissionService, runExecutor);
    }

    @Test
    void coordinatesSubmissionAndTheSharedExecutor() {
        ClusteringSubmissionResult submission = submission();
        when(submissionService.submit(2, "admin-1")).thenReturn(submission);
        when(runExecutor.executePendingRun(submission.runId())).thenReturn(executionSuccess());

        ClusteringExecutionResult result = orchestrator.execute(
                new CommunityClusteringExecutionCommand(2, "admin-1")
        );

        assertThat(result.outcome()).isEqualTo(ClusteringExecutionOutcome.SUCCESS);
        assertThat(result.runId()).isEqualTo(submission.runId());
        verify(submissionService).submit(2, "admin-1");
        verify(runExecutor).executePendingRun(submission.runId());
    }

    @Test
    void mapsBusinessSubmissionRejectionsWithoutCallingExecutor() {
        for (ClusteringRunFailureCode code : new ClusteringRunFailureCode[]{
                ClusteringRunFailureCode.NO_EFFECTIVE_USERS,
                ClusteringRunFailureCode.INVALID_CLUSTER_COUNT,
                ClusteringRunFailureCode.ACTIVE_RUN_EXISTS
        }) {
            org.mockito.Mockito.reset(submissionService, runExecutor);
            when(submissionService.submit(2, "admin-1"))
                    .thenThrow(new ClusteringSubmissionException(code, 1, 2));

            ClusteringExecutionResult result = orchestrator.execute(
                    new CommunityClusteringExecutionCommand(2, "admin-1")
            );

            assertThat(result.outcome())
                    .isEqualTo(ClusteringExecutionOutcome.PRECONDITION_REJECTED);
            assertThat(result.failureCode()).isEqualTo(code);
            verify(runExecutor, never()).executePendingRun(org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void mapsSubmissionInfrastructureFailureWithoutCallingExecutor() {
        when(submissionService.submit(2, "admin-1"))
                .thenThrow(new ClusteringSubmissionException(
                        ClusteringRunFailureCode.INTERNAL_ERROR,
                        2,
                        2
                ));

        ClusteringExecutionResult result = orchestrator.execute(
                new CommunityClusteringExecutionCommand(2, "admin-1")
        );

        assertThat(result.outcome()).isEqualTo(ClusteringExecutionOutcome.PRE_RUN_FAILED);
        verifyNoInteractions(runExecutor);
    }

    @Test
    void mapsEverySharedExecutorOutcome() {
        for (ClusteringRunExecutionOutcome outcome : ClusteringRunExecutionOutcome.values()) {
            org.mockito.Mockito.reset(submissionService, runExecutor);
            when(submissionService.submit(2, "admin-1")).thenReturn(submission());
            when(runExecutor.executePendingRun("run-1")).thenReturn(execution(outcome));

            ClusteringExecutionResult result = orchestrator.execute(
                    new CommunityClusteringExecutionCommand(2, "admin-1")
            );

            assertThat(result.outcome()).isEqualTo(switch (outcome) {
                case SUCCESS -> ClusteringExecutionOutcome.SUCCESS;
                case RUN_FAILED -> ClusteringExecutionOutcome.RUN_FAILED;
                case NOT_EXECUTABLE, RUN_FAILURE_NOT_RECORDED ->
                        ClusteringExecutionOutcome.RUN_FAILURE_NOT_RECORDED;
                case TERMINAL_STATE_CONFLICT ->
                        ClusteringExecutionOutcome.TERMINAL_STATE_CONFLICT;
            });
        }
    }

    @Test
    void orchestratorHasOnlyCoordinationDependenciesAndNoTransaction() {
        assertThat(Arrays.stream(CommunityClusteringOrchestrator.class.getDeclaredFields())
                .map(Field::getType))
                .containsExactlyInAnyOrder(
                        CommunityClusteringSubmissionService.class,
                        CommunityClusteringRunExecutor.class
                );
        assertThat(Arrays.stream(CommunityClusteringOrchestrator.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getAnnotations())))
                .noneMatch(annotation -> annotation.annotationType().getName()
                        .equals("org.springframework.transaction.annotation.Transactional"));
    }

    private static ClusteringSubmissionResult submission() {
        return new ClusteringSubmissionResult(
                "run-1",
                "version-1",
                ClusteringAlgorithm.KMEANS,
                2,
                42,
                ClusteringRunStatus.PENDING,
                Instant.parse("2026-07-21T00:00:00Z")
        );
    }

    private static ClusteringRunExecutionResult executionSuccess() {
        return execution(ClusteringRunExecutionOutcome.SUCCESS);
    }

    private static ClusteringRunExecutionResult execution(
            ClusteringRunExecutionOutcome outcome
    ) {
        return new ClusteringRunExecutionResult(
                outcome,
                "run-1",
                "version-1",
                switch (outcome) {
                    case SUCCESS, TERMINAL_STATE_CONFLICT -> ClusteringRunStatus.SUCCESS;
                    case RUN_FAILED -> ClusteringRunStatus.FAILED;
                    case NOT_EXECUTABLE -> ClusteringRunStatus.PENDING;
                    case RUN_FAILURE_NOT_RECORDED -> null;
                },
                outcome == ClusteringRunExecutionOutcome.SUCCESS
                        ? null
                        : ClusteringRunFailureCode.INTERNAL_ERROR,
                outcome == ClusteringRunExecutionOutcome.RUN_FAILED,
                2,
                2
        );
    }
}
