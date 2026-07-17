package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.client.clustering.dto.ClusteringMetrics;
import com.example.campusactivity.client.clustering.dto.ClusteringRequest;
import com.example.campusactivity.client.clustering.dto.ClusteringResponse;
import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.client.clustering.exception.ClusteringRemoteException;
import com.example.campusactivity.client.clustering.exception.ClusteringServiceUnavailableException;
import com.example.campusactivity.client.clustering.exception.InvalidClusteringServiceResponseException;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityClusteringOrchestratorTest {
    @Mock
    private CommunityFeatureAggregationService featureAggregationService;
    @Mock
    private ClusteringRunLifecycleService lifecycleService;
    @Mock
    private ClusteringRunFailureService failureService;
    @Mock
    private ClusteringClient clusteringClient;
    @Mock
    private ClusteringResponseValidator responseValidator;
    @Mock
    private ClusteringResultPersistenceService persistenceService;

    @Test
    void commandEnforcesFixedConstraintsWithoutRewritingCreatedBy() {
        String createdBy = "  admin-audit  ";
        CommunityClusteringExecutionCommand command =
                new CommunityClusteringExecutionCommand(2, createdBy);

        assertThat(command.clusterCount()).isEqualTo(2);
        assertThat(command.createdBy()).isSameAs(createdBy);

        assertThatThrownBy(() -> new CommunityClusteringExecutionCommand(1, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clusterCount 必须至少为2")
                .hasMessageNotContaining("1");
        for (String invalid : Arrays.asList(null, " ", "x".repeat(256))) {
            assertThatThrownBy(() -> new CommunityClusteringExecutionCommand(2, invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("createdBy 不符合聚类运行字段约束")
                    .hasMessageNotContaining("x".repeat(256));
        }
    }

    @Test
    void nullCommandIsRejectedWithFixedExceptionBeforeAnyDependencyInteraction() {
        CommunityClusteringOrchestrator orchestrator = orchestrator();

        assertThatThrownBy(() -> orchestrator.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("聚类执行命令不能为空")
                .hasNoCause();

        verifyNoInteractions(
                featureAggregationService,
                lifecycleService,
                failureService,
                clusteringClient,
                responseValidator,
                persistenceService
        );
    }

    @Test
    void executesCompleteSuccessInOrderAndUsesOnlyRunningSnapshotForRequest() {
        CommunityClusteringOrchestrator orchestrator = orchestrator();
        List<FeatureSample> samples = samples(4);
        FeatureAggregationResult aggregation = aggregation(samples);
        ClusteringRunSnapshot pending = snapshot(
                "pending-run",
                "pending-version",
                3,
                ClusteringRunStatus.PENDING
        );
        ClusteringRunSnapshot running = snapshot(
                "running-run",
                "running-version",
                4,
                ClusteringRunStatus.RUNNING
        );
        ClusteringRunSnapshot success = snapshot(
                "running-run",
                "running-version",
                4,
                ClusteringRunStatus.SUCCESS
        );
        ClusteringResponse response = mock(ClusteringResponse.class);
        ValidatedClusteringResult validated = mock(ValidatedClusteringResult.class);

        when(featureAggregationService.aggregateFeatureSamples()).thenReturn(aggregation);
        when(lifecycleService.createPending(any())).thenReturn(pending);
        when(lifecycleService.markRunning("pending-run")).thenReturn(running);
        when(clusteringClient.runClustering(any())).thenReturn(response);
        when(responseValidator.validate(any(), eq(response))).thenReturn(validated);
        when(persistenceService.persistSuccess("running-run", validated)).thenReturn(success);

        ClusteringExecutionResult result = orchestrator.execute(
                new CommunityClusteringExecutionCommand(2, "admin-success")
        );

        assertThat(result).isEqualTo(ClusteringExecutionResult.success(
                "running-run",
                "running-version",
                4,
                4
        ));

        InOrder order = inOrder(
                featureAggregationService,
                lifecycleService,
                clusteringClient,
                responseValidator,
                persistenceService
        );
        order.verify(featureAggregationService).aggregateFeatureSamples();
        order.verify(lifecycleService).createPending(any());
        order.verify(lifecycleService).markRunning("pending-run");
        order.verify(clusteringClient).runClustering(any());
        order.verify(responseValidator).validate(any(), eq(response));
        order.verify(persistenceService).persistSuccess("running-run", validated);

        ArgumentCaptor<ClusteringRunCreationCommand> creationCaptor =
                ArgumentCaptor.forClass(ClusteringRunCreationCommand.class);
        verify(lifecycleService).createPending(creationCaptor.capture());
        assertThat(creationCaptor.getValue()).isEqualTo(
                new ClusteringRunCreationCommand(2, 4, "admin-success")
        );

        ArgumentCaptor<ClusteringRequest> requestCaptor =
                ArgumentCaptor.forClass(ClusteringRequest.class);
        verify(clusteringClient).runClustering(requestCaptor.capture());
        ClusteringRequest request = requestCaptor.getValue();
        assertThat(request.runId()).isEqualTo("running-run");
        assertThat(request.version()).isEqualTo("running-version");
        assertThat(request.algorithm()).isEqualTo("KMEANS");
        assertThat(request.clusterCount()).isEqualTo(4);
        assertThat(request.randomState()).isEqualTo(42);
        assertThat(request.featureSchemaVersion()).isEqualTo("community-features-v1");
        assertThat(request.samples()).containsExactlyElementsOf(samples).isNotSameAs(samples);
        verify(responseValidator).validate(request, response);
        verify(persistenceService).persistSuccess("running-run", validated);
        verify(failureService, never()).markFailed(any(), any());
        verify(clusteringClient, never()).health();
        verify(clusteringClient, times(1)).runClustering(any());
    }

    @Test
    void aggregationFailureReturnsFixedPreRunFailureWithoutCreatingRun() {
        CommunityClusteringOrchestrator orchestrator = orchestrator();
        when(featureAggregationService.aggregateFeatureSamples())
                .thenThrow(new IllegalStateException("dynamic user and URL data"));

        ClusteringExecutionResult result = orchestrator.execute(command());

        assertThat(result).isEqualTo(ClusteringExecutionResult.preRunFailed(
                ClusteringRunFailureCode.FEATURE_AGGREGATION_FAILED,
                0,
                2
        ));
        verifyNoInteractions(
                lifecycleService,
                failureService,
                clusteringClient,
                responseValidator,
                persistenceService
        );
    }

    @Test
    void insufficientSamplesAndOversizedClusterCountRejectBeforeRunCreation() {
        CommunityClusteringOrchestrator orchestrator = orchestrator();
        when(featureAggregationService.aggregateFeatureSamples())
                .thenReturn(aggregation(samples(1)), aggregation(samples(2)));

        ClusteringExecutionResult insufficient = orchestrator.execute(command());
        ClusteringExecutionResult oversized = orchestrator.execute(
                new CommunityClusteringExecutionCommand(3, "admin")
        );

        assertThat(insufficient).isEqualTo(ClusteringExecutionResult.preconditionRejected(
                ClusteringRunFailureCode.NO_EFFECTIVE_USERS,
                1,
                2
        ));
        assertThat(oversized).isEqualTo(ClusteringExecutionResult.preconditionRejected(
                ClusteringRunFailureCode.INVALID_CLUSTER_COUNT,
                2,
                3
        ));
        verifyNoInteractions(
                lifecycleService,
                failureService,
                clusteringClient,
                responseValidator,
                persistenceService
        );
    }

    @Test
    void activeRunAndCreationConflictDoNotRecordFailureOrCallPython() {
        CommunityClusteringOrchestrator orchestrator = orchestrator();
        when(featureAggregationService.aggregateFeatureSamples())
                .thenReturn(aggregation(samples(2)));
        when(lifecycleService.createPending(any()))
                .thenThrow(new ClusteringRunStateException(ClusteringRunStateCode.ACTIVE_RUN_EXISTS))
                .thenThrow(new ClusteringRunStateException(ClusteringRunStateCode.RUN_CREATION_CONFLICT));

        ClusteringExecutionResult active = orchestrator.execute(command());
        ClusteringExecutionResult collision = orchestrator.execute(command());

        assertThat(active).isEqualTo(ClusteringExecutionResult.preconditionRejected(
                ClusteringRunFailureCode.ACTIVE_RUN_EXISTS,
                2,
                2
        ));
        assertThat(collision).isEqualTo(ClusteringExecutionResult.preRunFailed(
                ClusteringRunFailureCode.INTERNAL_ERROR,
                2,
                2
        ));
        verifyNoInteractions(failureService, clusteringClient, responseValidator, persistenceService);
    }

    @ParameterizedTest
    @MethodSource("markRunningFailures")
    void markRunningFailuresUseFixedMappingAndAttemptFailureOnce(
            ClusteringRunStateCode stateCode,
            ClusteringRunFailureCode expectedCode
    ) {
        CommunityClusteringOrchestrator orchestrator = orchestrator();
        ClusteringRunSnapshot pending = pending();
        when(featureAggregationService.aggregateFeatureSamples())
                .thenReturn(aggregation(samples(2)));
        when(lifecycleService.createPending(any())).thenReturn(pending);
        when(lifecycleService.markRunning(pending.runId()))
                .thenThrow(new ClusteringRunStateException(stateCode));
        when(failureService.markFailed(pending.runId(), expectedCode))
                .thenReturn(failureResult(FailureRecordingResult.Outcome.RECORDED, failed(pending)));

        ClusteringExecutionResult result = orchestrator.execute(command());

        assertThat(result.outcome()).isEqualTo(ClusteringExecutionOutcome.RUN_FAILED);
        assertThat(result.failureCode()).isEqualTo(expectedCode);
        verify(failureService, times(1)).markFailed(pending.runId(), expectedCode);
        verifyNoInteractions(clusteringClient, responseValidator, persistenceService);
    }

    @ParameterizedTest
    @MethodSource("clientFailures")
    void clientFailuresUseFixedMappingWithoutRetry(
            RuntimeException clientFailure,
            ClusteringRunFailureCode expectedCode
    ) {
        ExecutionFixture fixture = prepareRunningExecution();
        when(clusteringClient.runClustering(any())).thenThrow(clientFailure);
        when(failureService.markFailed(fixture.running().runId(), expectedCode))
                .thenReturn(failureResult(
                        FailureRecordingResult.Outcome.RECORDED,
                        failed(fixture.running())
                ));

        ClusteringExecutionResult result = fixture.orchestrator().execute(command());

        assertThat(result.outcome()).isEqualTo(ClusteringExecutionOutcome.RUN_FAILED);
        assertThat(result.failureCode()).isEqualTo(expectedCode);
        verify(clusteringClient, times(1)).runClustering(any());
        verify(clusteringClient, never()).health();
        verify(failureService, times(1))
                .markFailed(fixture.running().runId(), expectedCode);
        verifyNoInteractions(responseValidator, persistenceService);
    }

    @Test
    void validationFailureUsesFixedCode() {
        ExecutionFixture fixture = prepareRunningExecution();
        ClusteringResponse response = mock(ClusteringResponse.class);
        when(clusteringClient.runClustering(any())).thenReturn(response);
        when(responseValidator.validate(any(), eq(response))).thenThrow(
                new ClusteringResponseValidationException(
                        ClusteringResponseValidationCode.RUN_ID_MISMATCH
                )
        );
        when(failureService.markFailed(
                fixture.running().runId(),
                ClusteringRunFailureCode.INVALID_CLUSTERING_RESULT
        )).thenReturn(failureResult(
                FailureRecordingResult.Outcome.RECORDED,
                failed(fixture.running())
        ));

        ClusteringExecutionResult result = fixture.orchestrator().execute(command());

        assertThat(result.failureCode())
                .isEqualTo(ClusteringRunFailureCode.INVALID_CLUSTERING_RESULT);
        verifyNoInteractions(persistenceService);
    }

    @ParameterizedTest
    @MethodSource("persistenceFailures")
    void persistenceFailuresUseFixedMapping(
            ClusteringRunStateCode stateCode,
            ClusteringRunFailureCode expectedCode
    ) {
        ExecutionFixture fixture = prepareRunningExecutionThroughValidation();
        when(persistenceService.persistSuccess(
                fixture.running().runId(),
                fixture.validated()
        )).thenThrow(new ClusteringRunStateException(stateCode));
        when(failureService.markFailed(fixture.running().runId(), expectedCode))
                .thenReturn(failureResult(
                        FailureRecordingResult.Outcome.RECORDED,
                        failed(fixture.running())
                ));

        ClusteringExecutionResult result = fixture.orchestrator().execute(command());

        assertThat(result.outcome()).isEqualTo(ClusteringExecutionOutcome.RUN_FAILED);
        assertThat(result.failureCode()).isEqualTo(expectedCode);
        verify(failureService, times(1))
                .markFailed(fixture.running().runId(), expectedCode);
    }

    @Test
    void unexpectedPersistenceFailureUsesInternalErrorWithoutReadingMessage() {
        ExecutionFixture fixture = prepareRunningExecutionThroughValidation();
        when(persistenceService.persistSuccess(
                fixture.running().runId(),
                fixture.validated()
        )).thenThrow(new IllegalStateException("secret URL userId and response body"));
        when(failureService.markFailed(
                fixture.running().runId(),
                ClusteringRunFailureCode.INTERNAL_ERROR
        )).thenReturn(failureResult(
                FailureRecordingResult.Outcome.RECORDED,
                failed(fixture.running())
        ));

        ClusteringExecutionResult result = fixture.orchestrator().execute(command());

        assertThat(result.failureCode()).isEqualTo(ClusteringRunFailureCode.INTERNAL_ERROR);
        assertThat(result.toString()).doesNotContain(
                "secret", "URL", "userId", "response body"
        );
    }

    @ParameterizedTest
    @EnumSource(value = FailureRecordingResult.Outcome.class)
    void mapsEveryFailureRecordingOutcome(FailureRecordingResult.Outcome outcome) {
        ExecutionFixture fixture = prepareRunningExecution();
        when(clusteringClient.runClustering(any()))
                .thenThrow(new InvalidClusteringServiceResponseException());
        ClusteringRunSnapshot terminal = switch (outcome) {
            case RECORDED, ALREADY_FAILED -> failed(fixture.running());
            case RUN_NOT_FOUND -> null;
            case TERMINAL_SUCCESS_CONFLICT -> snapshot(
                    fixture.running().runId(),
                    fixture.running().version(),
                    fixture.running().clusterCount(),
                    ClusteringRunStatus.SUCCESS
            );
        };
        when(failureService.markFailed(
                fixture.running().runId(),
                ClusteringRunFailureCode.PYTHON_PROTOCOL_ERROR
        )).thenReturn(failureResult(outcome, terminal));

        ClusteringExecutionResult result = fixture.orchestrator().execute(command());

        switch (outcome) {
            case RECORDED, ALREADY_FAILED -> {
                assertThat(result.outcome()).isEqualTo(ClusteringExecutionOutcome.RUN_FAILED);
                assertThat(result.failureRecorded()).isTrue();
                assertThat(result.finalStatus()).isEqualTo(ClusteringRunStatus.FAILED);
            }
            case RUN_NOT_FOUND -> {
                assertThat(result.outcome())
                        .isEqualTo(ClusteringExecutionOutcome.RUN_FAILURE_NOT_RECORDED);
                assertThat(result.failureRecorded()).isFalse();
                assertThat(result.finalStatus()).isNull();
            }
            case TERMINAL_SUCCESS_CONFLICT -> {
                assertThat(result.outcome())
                        .isEqualTo(ClusteringExecutionOutcome.TERMINAL_STATE_CONFLICT);
                assertThat(result.failureRecorded()).isFalse();
                assertThat(result.finalStatus()).isEqualTo(ClusteringRunStatus.SUCCESS);
            }
        }
    }

    @Test
    void failureRecordingExceptionCannotPretendFailureWasRecorded() {
        ExecutionFixture fixture = prepareRunningExecution();
        when(clusteringClient.runClustering(any()))
                .thenThrow(new InvalidClusteringServiceResponseException());
        when(failureService.markFailed(any(), any()))
                .thenThrow(new IllegalStateException("dynamic persistence message"));

        ClusteringExecutionResult result = fixture.orchestrator().execute(command());

        assertThat(result.outcome())
                .isEqualTo(ClusteringExecutionOutcome.RUN_FAILURE_NOT_RECORDED);
        assertThat(result.failureRecorded()).isFalse();
        assertThat(result.finalStatus()).isNull();
        verify(failureService, times(1)).markFailed(
                fixture.running().runId(),
                ClusteringRunFailureCode.PYTHON_PROTOCOL_ERROR
        );
    }

    @Test
    void secondTransactionGuardMarksRunFailedWithoutCallingPython() {
        CommunityClusteringOrchestrator orchestrator = orchestrator();
        ClusteringRunSnapshot pending = pending();
        ClusteringRunSnapshot running = running();
        when(featureAggregationService.aggregateFeatureSamples())
                .thenReturn(aggregation(samples(2)));
        when(lifecycleService.createPending(any())).thenReturn(pending);
        when(lifecycleService.markRunning(pending.runId())).thenReturn(running);
        when(failureService.markFailed(
                running.runId(),
                ClusteringRunFailureCode.INTERNAL_ERROR
        )).thenReturn(failureResult(
                FailureRecordingResult.Outcome.RECORDED,
                failed(running)
        ));

        try (MockedStatic<TransactionSynchronizationManager> transactions =
                     mockStatic(TransactionSynchronizationManager.class)) {
            transactions.when(TransactionSynchronizationManager::isActualTransactionActive)
                    .thenReturn(false, true);

            ClusteringExecutionResult result = orchestrator.execute(command());

            assertThat(result.outcome()).isEqualTo(ClusteringExecutionOutcome.RUN_FAILED);
            assertThat(result.failureCode()).isEqualTo(ClusteringRunFailureCode.INTERNAL_ERROR);
            transactions.verify(
                    TransactionSynchronizationManager::isActualTransactionActive,
                    times(2)
            );
        }
        verifyNoInteractions(clusteringClient, responseValidator, persistenceService);
        verify(failureService, times(1)).markFailed(
                running.runId(),
                ClusteringRunFailureCode.INTERNAL_ERROR
        );
    }

    @Test
    void orchestratorHasNoTransactionalBoundaryAndOnlySixAllowedDependencies() throws Exception {
        assertThat(CommunityClusteringOrchestrator.class.getAnnotation(Transactional.class)).isNull();
        Method execute = CommunityClusteringOrchestrator.class.getMethod(
                "execute",
                CommunityClusteringExecutionCommand.class
        );
        assertThat(execute.getAnnotation(Transactional.class)).isNull();

        List<Class<?>> fieldTypes = Arrays.stream(
                        CommunityClusteringOrchestrator.class.getDeclaredFields()
                )
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .toList();
        assertThat(fieldTypes).containsExactlyInAnyOrder(
                CommunityFeatureAggregationService.class,
                ClusteringRunLifecycleService.class,
                ClusteringRunFailureService.class,
                ClusteringClient.class,
                ClusteringResponseValidator.class,
                ClusteringResultPersistenceService.class
        );
        assertThat(CommunityClusteringOrchestrator.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(
                                CommunityFeatureAggregationService.class,
                                ClusteringRunLifecycleService.class,
                                ClusteringRunFailureService.class,
                                ClusteringClient.class,
                                ClusteringResponseValidator.class,
                                ClusteringResultPersistenceService.class
                        ));
    }

    @Test
    void executionResultContainsNoSensitiveOrDynamicCarrierFields() {
        Set<Class<?>> allowedTypes = Set.of(
                ClusteringExecutionOutcome.class,
                String.class,
                ClusteringRunStatus.class,
                ClusteringRunFailureCode.class,
                boolean.class,
                int.class
        );
        assertThat(Arrays.stream(ClusteringExecutionResult.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType))
                .allMatch(allowedTypes::contains);
        assertThat(Arrays.stream(ClusteringExecutionResult.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain(
                        "samples",
                        "userId",
                        "interests",
                        "remoteMessage",
                        "details",
                        "url",
                        "responseBody",
                        "exception",
                        "message"
                );
    }

    private CommunityClusteringOrchestrator orchestrator() {
        return new CommunityClusteringOrchestrator(
                featureAggregationService,
                lifecycleService,
                failureService,
                clusteringClient,
                responseValidator,
                persistenceService
        );
    }

    private ExecutionFixture prepareRunningExecution() {
        CommunityClusteringOrchestrator orchestrator = orchestrator();
        ClusteringRunSnapshot pending = pending();
        ClusteringRunSnapshot running = running();
        when(featureAggregationService.aggregateFeatureSamples())
                .thenReturn(aggregation(samples(2)));
        when(lifecycleService.createPending(any())).thenReturn(pending);
        when(lifecycleService.markRunning(pending.runId())).thenReturn(running);
        return new ExecutionFixture(orchestrator, running, null);
    }

    private ExecutionFixture prepareRunningExecutionThroughValidation() {
        ExecutionFixture fixture = prepareRunningExecution();
        ClusteringResponse response = mock(ClusteringResponse.class);
        ValidatedClusteringResult validated = mock(ValidatedClusteringResult.class);
        when(clusteringClient.runClustering(any())).thenReturn(response);
        when(responseValidator.validate(any(), eq(response))).thenReturn(validated);
        return new ExecutionFixture(fixture.orchestrator(), fixture.running(), validated);
    }

    private static CommunityClusteringExecutionCommand command() {
        return new CommunityClusteringExecutionCommand(2, "admin");
    }

    private static FeatureAggregationResult aggregation(List<FeatureSample> samples) {
        return new FeatureAggregationResult(
                samples,
                List.of(),
                new FeatureAggregationDiagnostics(0, 0, 0, 0, 0, 0, 0, 0)
        );
    }

    private static List<FeatureSample> samples(int count) {
        return Stream.iterate(1, value -> value + 1)
                .limit(count)
                .map(value -> new FeatureSample(
                        "user-" + value,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        value,
                        0,
                        0,
                        0,
                        0,
                        null,
                        Map.of()
                ))
                .toList();
    }

    private static ClusteringRunSnapshot pending() {
        return snapshot("run-1", "version-1", 2, ClusteringRunStatus.PENDING);
    }

    private static ClusteringRunSnapshot running() {
        return snapshot("run-1", "version-1", 2, ClusteringRunStatus.RUNNING);
    }

    private static ClusteringRunSnapshot failed(ClusteringRunSnapshot source) {
        return snapshot(
                source.runId(),
                source.version(),
                source.clusterCount(),
                ClusteringRunStatus.FAILED
        );
    }

    private static ClusteringRunSnapshot snapshot(
            String runId,
            String version,
            int clusterCount,
            ClusteringRunStatus status
    ) {
        return new ClusteringRunSnapshot(
                runId,
                version,
                ClusteringAlgorithm.KMEANS,
                clusterCount,
                42,
                status,
                4,
                "community-features-v1",
                "{}",
                null,
                null,
                null,
                null,
                "admin",
                null
        );
    }

    private static FailureRecordingResult failureResult(
            FailureRecordingResult.Outcome outcome,
            ClusteringRunSnapshot run
    ) {
        return new FailureRecordingResult(outcome, Optional.ofNullable(run));
    }

    private static Stream<Arguments> markRunningFailures() {
        return Stream.of(
                Arguments.of(
                        ClusteringRunStateCode.RUN_NOT_FOUND,
                        ClusteringRunFailureCode.RUN_STATE_CONFLICT
                ),
                Arguments.of(
                        ClusteringRunStateCode.INVALID_STATE_TRANSITION,
                        ClusteringRunFailureCode.RUN_STATE_CONFLICT
                ),
                Arguments.of(
                        ClusteringRunStateCode.RUN_ALREADY_TERMINAL,
                        ClusteringRunFailureCode.RUN_STATE_CONFLICT
                ),
                Arguments.of(
                        ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED,
                        ClusteringRunFailureCode.RESULT_PERSISTENCE_FAILED
                )
        );
    }

    private static Stream<Arguments> clientFailures() {
        return Stream.of(
                Arguments.of(
                        new ClusteringRemoteException(
                                422,
                                "CLUSTERING_COMPUTATION_FAILED",
                                "dynamic remote message",
                                Map.of("userId", "secret")
                        ),
                        ClusteringRunFailureCode.PYTHON_REQUEST_REJECTED
                ),
                Arguments.of(
                        new ClusteringServiceUnavailableException(503, "SERVICE_UNAVAILABLE"),
                        ClusteringRunFailureCode.PYTHON_SERVICE_UNAVAILABLE
                ),
                Arguments.of(
                        new ClusteringServiceUnavailableException(),
                        ClusteringRunFailureCode.PYTHON_SERVICE_UNAVAILABLE
                ),
                Arguments.of(
                        new InvalidClusteringServiceResponseException(),
                        ClusteringRunFailureCode.PYTHON_PROTOCOL_ERROR
                )
        );
    }

    private static Stream<Arguments> persistenceFailures() {
        return Stream.of(
                Arguments.of(
                        ClusteringRunStateCode.RUN_RESULT_MISMATCH,
                        ClusteringRunFailureCode.INVALID_CLUSTERING_RESULT
                ),
                Arguments.of(
                        ClusteringRunStateCode.USER_REFERENCE_MISSING,
                        ClusteringRunFailureCode.USER_REFERENCE_MISSING
                ),
                Arguments.of(
                        ClusteringRunStateCode.RESULT_SERIALIZATION_FAILED,
                        ClusteringRunFailureCode.RESULT_SERIALIZATION_FAILED
                ),
                Arguments.of(
                        ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED,
                        ClusteringRunFailureCode.RESULT_PERSISTENCE_FAILED
                ),
                Arguments.of(
                        ClusteringRunStateCode.RUN_NOT_FOUND,
                        ClusteringRunFailureCode.RUN_STATE_CONFLICT
                ),
                Arguments.of(
                        ClusteringRunStateCode.INVALID_STATE_TRANSITION,
                        ClusteringRunFailureCode.RUN_STATE_CONFLICT
                ),
                Arguments.of(
                        ClusteringRunStateCode.RUN_ALREADY_TERMINAL,
                        ClusteringRunFailureCode.RUN_STATE_CONFLICT
                ),
                Arguments.of(
                        ClusteringRunStateCode.RUN_RESULTS_ALREADY_EXIST,
                        ClusteringRunFailureCode.RUN_STATE_CONFLICT
                )
        );
    }

    private record ExecutionFixture(
            CommunityClusteringOrchestrator orchestrator,
            ClusteringRunSnapshot running,
            ValidatedClusteringResult validated
    ) {
    }
}
