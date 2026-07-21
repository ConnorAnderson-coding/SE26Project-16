package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.client.clustering.dto.ClusteringRequest;
import com.example.campusactivity.client.clustering.dto.ClusteringResponse;
import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.client.clustering.exception.ClusteringServiceUnavailableException;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunInput;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.repository.ClusteringRunInputRepository;
import com.example.campusactivity.repository.ClusteringRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityClusteringRunExecutorTest {
    @Mock
    private ClusteringRunLifecycleService lifecycleService;
    @Mock
    private ClusteringRunRepository runRepository;
    @Mock
    private ClusteringRunInputRepository inputRepository;
    @Mock
    private ClusteringRunInputCodec inputCodec;
    @Mock
    private ClusteringRunFailureService failureService;
    @Mock
    private ClusteringClient clusteringClient;
    @Mock
    private ClusteringResponseValidator responseValidator;
    @Mock
    private ClusteringResultPersistenceService persistenceService;

    private CommunityClusteringRunExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new CommunityClusteringRunExecutor(
                lifecycleService,
                runRepository,
                inputRepository,
                inputCodec,
                failureService,
                clusteringClient,
                responseValidator,
                persistenceService
        );
    }

    @Test
    void executesPendingRunFromPersistedInputsThroughSuccess() {
        Prepared prepared = prepareValidExecution();

        ClusteringRunExecutionResult result = executor.executePendingRun("run-1");

        assertThat(result.outcome()).isEqualTo(ClusteringRunExecutionOutcome.SUCCESS);
        assertThat(result.finalStatus()).isEqualTo(ClusteringRunStatus.SUCCESS);
        ArgumentCaptor<ClusteringRequest> request = ArgumentCaptor.forClass(ClusteringRequest.class);
        verify(clusteringClient).runClustering(request.capture());
        assertThat(request.getValue().samples()).containsExactly(
                prepared.firstSample(),
                prepared.secondSample()
        );
        assertThat(request.getValue().clusterCount()).isEqualTo(2);
        assertThat(request.getValue().randomState()).isEqualTo(42);
        assertThat(request.getValue().featureSchemaVersion())
                .isEqualTo("community-features-v1");
        verify(responseValidator).validate(request.getValue(), prepared.response());
        verify(persistenceService).persistSuccess("run-1", prepared.validated());
        verifyNoInteractions(failureService);
    }

    @Test
    void remoteCallIsOutsideTransaction() {
        prepareValidExecution();
        when(clusteringClient.runClustering(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return org.mockito.Mockito.mock(ClusteringResponse.class);
        });

        executor.executePendingRun("run-1");

        verify(clusteringClient).runClustering(any());
    }

    @Test
    void missingInputsFailWithFixedSnapshotCodeWithoutPython() {
        prepareRunningWithInputs(List.of());
        stubRecordedFailure(ClusteringRunFailureCode.INPUT_SNAPSHOT_INVALID);

        ClusteringRunExecutionResult result = executor.executePendingRun("run-1");

        assertThat(result.outcome()).isEqualTo(ClusteringRunExecutionOutcome.RUN_FAILED);
        assertThat(result.failureCode())
                .isEqualTo(ClusteringRunFailureCode.INPUT_SNAPSHOT_INVALID);
        verify(clusteringClient, never()).runClustering(any());
    }

    @Test
    void duplicateInputUsersFailBeforePython() {
        ClusteringRunInput first = input(0, "student-1");
        ClusteringRunInput duplicate = input(1, "student-1");
        prepareRunningWithInputs(List.of(first, duplicate));
        stubRecordedFailure(ClusteringRunFailureCode.INPUT_SNAPSHOT_INVALID);

        ClusteringRunExecutionResult result = executor.executePendingRun("run-1");

        assertThat(result.failureCode())
                .isEqualTo(ClusteringRunFailureCode.INPUT_SNAPSHOT_INVALID);
        verify(clusteringClient, never()).runClustering(any());
    }

    @Test
    void inputCountMismatchFailsBeforePython() {
        prepareRunningWithInputs(List.of(input(0, "student-1")));
        stubRecordedFailure(ClusteringRunFailureCode.INPUT_SNAPSHOT_INVALID);

        ClusteringRunExecutionResult result = executor.executePendingRun("run-1");

        assertThat(result.failureCode())
                .isEqualTo(ClusteringRunFailureCode.INPUT_SNAPSHOT_INVALID);
        verify(clusteringClient, never()).runClustering(any());
    }

    @Test
    void missingRunIsNotExecutableAndDoesNotTouchPython() {
        when(runRepository.findById("missing")).thenReturn(Optional.empty());

        ClusteringRunExecutionResult result = executor.executePendingRun("missing");

        assertThat(result.outcome()).isEqualTo(ClusteringRunExecutionOutcome.NOT_EXECUTABLE);
        verifyNoInteractions(lifecycleService, inputRepository, clusteringClient);
    }

    @Test
    void nonPendingRunIsNotExecuted() {
        ClusteringRun success = run(ClusteringRunStatus.SUCCESS);
        when(runRepository.findById("run-1")).thenReturn(Optional.of(success));
        when(lifecycleService.markRunning("run-1"))
                .thenThrow(new ClusteringRunStateException(
                        ClusteringRunStateCode.RUN_ALREADY_TERMINAL
                ));

        ClusteringRunExecutionResult result = executor.executePendingRun("run-1");

        assertThat(result.outcome()).isEqualTo(ClusteringRunExecutionOutcome.NOT_EXECUTABLE);
        verifyNoInteractions(inputRepository, clusteringClient, failureService);
    }

    @Test
    void pythonFailureIsSanitizedAndRecordedWithFixedMapping() {
        prepareValidExecution();
        when(clusteringClient.runClustering(any()))
                .thenThrow(new ClusteringServiceUnavailableException());
        stubRecordedFailure(ClusteringRunFailureCode.PYTHON_SERVICE_UNAVAILABLE);

        ClusteringRunExecutionResult result = executor.executePendingRun("run-1");

        assertThat(result.outcome()).isEqualTo(ClusteringRunExecutionOutcome.RUN_FAILED);
        assertThat(result.failureCode())
                .isEqualTo(ClusteringRunFailureCode.PYTHON_SERVICE_UNAVAILABLE);
        verify(responseValidator, never()).validate(any(), any());
        verify(persistenceService, never()).persistSuccess(any(), any());
    }

    @Test
    void claimedRunningRunSkipsSecondClaimAndUsesSameExecutionPath() {
        Prepared prepared = prepareValidExecution();
        when(runRepository.findById("run-1"))
                .thenReturn(Optional.of(run(ClusteringRunStatus.RUNNING)));

        ClusteringRunExecutionResult result = executor.executeClaimedRun("run-1");

        assertThat(result.outcome()).isEqualTo(ClusteringRunExecutionOutcome.SUCCESS);
        verify(lifecycleService, never()).markRunning(any());
        verify(responseValidator).validate(any(), org.mockito.ArgumentMatchers.same(prepared.response()));
    }

    private Prepared prepareValidExecution() {
        ClusteringRunInput first = input(0, "student-1");
        ClusteringRunInput second = input(1, "student-2");
        prepareRunningWithInputs(List.of(first, second));
        FeatureSample firstSample = sample("student-1");
        FeatureSample secondSample = sample("student-2");
        when(inputCodec.decode(first)).thenReturn(firstSample);
        when(inputCodec.decode(second)).thenReturn(secondSample);
        ClusteringResponse response = org.mockito.Mockito.mock(ClusteringResponse.class);
        ValidatedClusteringResult validated = org.mockito.Mockito.mock(
                ValidatedClusteringResult.class
        );
        org.mockito.Mockito.lenient().when(clusteringClient.runClustering(any()))
                .thenReturn(response);
        org.mockito.Mockito.lenient().when(responseValidator.validate(
                        any(),
                        org.mockito.ArgumentMatchers.same(response)
                ))
                .thenReturn(validated);
        org.mockito.Mockito.lenient().when(persistenceService.persistSuccess("run-1", validated))
                .thenReturn(snapshot(ClusteringRunStatus.SUCCESS));
        return new Prepared(firstSample, secondSample, response, validated);
    }

    private void prepareRunningWithInputs(List<ClusteringRunInput> inputs) {
        when(runRepository.findById("run-1"))
                .thenReturn(Optional.of(run(ClusteringRunStatus.PENDING)));
        org.mockito.Mockito.lenient().when(lifecycleService.markRunning("run-1"))
                .thenReturn(snapshot(ClusteringRunStatus.RUNNING));
        when(inputRepository.findByRunIdOrderBySampleOrderAsc("run-1"))
                .thenReturn(inputs);
    }

    private void stubRecordedFailure(ClusteringRunFailureCode code) {
        when(failureService.markFailed("run-1", code)).thenReturn(
                FailureRecordingResult.of(
                        FailureRecordingResult.Outcome.RECORDED,
                        snapshot(ClusteringRunStatus.FAILED)
                )
        );
    }

    private static ClusteringRunInput input(int order, String userId) {
        ClusteringRunInput input = org.mockito.Mockito.mock(ClusteringRunInput.class);
        org.mockito.Mockito.lenient().when(input.getSampleOrder()).thenReturn(order);
        org.mockito.Mockito.lenient().when(input.getUserId()).thenReturn(userId);
        return input;
    }

    private static FeatureSample sample(String userId) {
        return new FeatureSample(
                userId,
                List.of("AI"),
                "Computer Science",
                "2026",
                List.of("MONDAY"),
                1,
                1,
                0,
                0,
                0,
                null,
                Map.of("Technology", 1)
        );
    }

    private static ClusteringRun run(ClusteringRunStatus status) {
        ClusteringRun run = new ClusteringRun();
        run.setId("run-1");
        run.setVersion("version-1");
        run.setAlgorithm(ClusteringAlgorithm.KMEANS);
        run.setClusterCount(2);
        run.setRandomState(42);
        run.setStatus(status);
        run.setSampleCount(2);
        run.setFeatureSchemaVersion("community-features-v1");
        run.setParametersJson("{}");
        run.setCreatedBy("admin-1");
        run.setCreatedAt(Instant.parse("2026-07-21T00:00:00Z"));
        if (status != ClusteringRunStatus.PENDING) {
            run.setStartedAt(Instant.parse("2026-07-21T00:00:01Z"));
        }
        if (status == ClusteringRunStatus.SUCCESS || status == ClusteringRunStatus.FAILED) {
            run.setFinishedAt(Instant.parse("2026-07-21T00:00:02Z"));
        }
        return run;
    }

    private static ClusteringRunSnapshot snapshot(ClusteringRunStatus status) {
        return ClusteringRunSnapshot.from(run(status));
    }

    private record Prepared(
            FeatureSample firstSample,
            FeatureSample secondSample,
            ClusteringResponse response,
            ValidatedClusteringResult validated
    ) {
    }
}
