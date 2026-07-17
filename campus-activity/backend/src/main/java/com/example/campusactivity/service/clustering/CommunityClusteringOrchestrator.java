package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.client.clustering.dto.ClusteringRequest;
import com.example.campusactivity.client.clustering.dto.ClusteringResponse;
import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.client.clustering.exception.ClusteringClientException;
import com.example.campusactivity.client.clustering.exception.ClusteringRemoteException;
import com.example.campusactivity.client.clustering.exception.ClusteringServiceUnavailableException;
import com.example.campusactivity.client.clustering.exception.InvalidClusteringServiceResponseException;
import com.example.campusactivity.entity.ClusteringRunStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@ConditionalOnProperty(
        prefix = "community-clustering.python",
        name = "enabled",
        havingValue = "true"
)
public class CommunityClusteringOrchestrator {
    private final CommunityFeatureAggregationService featureAggregationService;
    private final ClusteringRunLifecycleService lifecycleService;
    private final ClusteringRunFailureService failureService;
    private final ClusteringClient clusteringClient;
    private final ClusteringResponseValidator responseValidator;
    private final ClusteringResultPersistenceService persistenceService;

    public CommunityClusteringOrchestrator(
            CommunityFeatureAggregationService featureAggregationService,
            ClusteringRunLifecycleService lifecycleService,
            ClusteringRunFailureService failureService,
            ClusteringClient clusteringClient,
            ClusteringResponseValidator responseValidator,
            ClusteringResultPersistenceService persistenceService
    ) {
        this.featureAggregationService = featureAggregationService;
        this.lifecycleService = lifecycleService;
        this.failureService = failureService;
        this.clusteringClient = clusteringClient;
        this.responseValidator = responseValidator;
        this.persistenceService = persistenceService;
    }

    public ClusteringExecutionResult execute(
            CommunityClusteringExecutionCommand command
    ) {
        boolean transactionActive =
                TransactionSynchronizationManager.isActualTransactionActive();
        if (command == null) {
            throw new IllegalArgumentException("聚类执行命令不能为空");
        }
        if (transactionActive) {
            return ClusteringExecutionResult.preRunFailed(
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    0,
                    command.clusterCount()
            );
        }

        List<FeatureSample> samples;
        try {
            FeatureAggregationResult aggregationResult =
                    featureAggregationService.aggregateFeatureSamples();
            samples = List.copyOf(aggregationResult.samples());
        } catch (RuntimeException _exception) {
            return ClusteringExecutionResult.preRunFailed(
                    ClusteringRunFailureCode.FEATURE_AGGREGATION_FAILED,
                    0,
                    command.clusterCount()
            );
        }

        int sampleCount = samples.size();
        if (sampleCount < 2) {
            return ClusteringExecutionResult.preconditionRejected(
                    ClusteringRunFailureCode.NO_EFFECTIVE_USERS,
                    sampleCount,
                    command.clusterCount()
            );
        }
        if (command.clusterCount() > sampleCount) {
            return ClusteringExecutionResult.preconditionRejected(
                    ClusteringRunFailureCode.INVALID_CLUSTER_COUNT,
                    sampleCount,
                    command.clusterCount()
            );
        }

        ClusteringRunSnapshot pending;
        try {
            pending = lifecycleService.createPending(new ClusteringRunCreationCommand(
                    command.clusterCount(),
                    sampleCount,
                    command.createdBy()
            ));
        } catch (ClusteringRunStateException exception) {
            if (exception.getCode() == ClusteringRunStateCode.ACTIVE_RUN_EXISTS) {
                return ClusteringExecutionResult.preconditionRejected(
                        ClusteringRunFailureCode.ACTIVE_RUN_EXISTS,
                        sampleCount,
                        command.clusterCount()
                );
            }
            return ClusteringExecutionResult.preRunFailed(
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    sampleCount,
                    command.clusterCount()
            );
        } catch (RuntimeException _exception) {
            return ClusteringExecutionResult.preRunFailed(
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    sampleCount,
                    command.clusterCount()
            );
        }

        ClusteringRunSnapshot running;
        try {
            running = lifecycleService.markRunning(pending.runId());
        } catch (ClusteringRunStateException exception) {
            return recordFailure(
                    pending,
                    mapMarkRunningFailure(exception.getCode()),
                    sampleCount
            );
        } catch (RuntimeException _exception) {
            return recordFailure(
                    pending,
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    sampleCount
            );
        }

        ClusteringRequest request;
        try {
            request = new ClusteringRequest(
                    running.runId(),
                    running.version(),
                    running.algorithm().name(),
                    running.clusterCount(),
                    running.randomState(),
                    running.featureSchemaVersion(),
                    samples
            );
        } catch (RuntimeException _exception) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    sampleCount
            );
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    sampleCount
            );
        }

        ClusteringResponse response;
        try {
            response = clusteringClient.runClustering(request);
        } catch (ClusteringRemoteException _exception) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.PYTHON_REQUEST_REJECTED,
                    sampleCount
            );
        } catch (ClusteringServiceUnavailableException _exception) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.PYTHON_SERVICE_UNAVAILABLE,
                    sampleCount
            );
        } catch (InvalidClusteringServiceResponseException _exception) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.PYTHON_PROTOCOL_ERROR,
                    sampleCount
            );
        } catch (ClusteringClientException _exception) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    sampleCount
            );
        } catch (RuntimeException _exception) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    sampleCount
            );
        }

        ValidatedClusteringResult validatedResult;
        try {
            validatedResult = responseValidator.validate(request, response);
        } catch (ClusteringResponseValidationException _exception) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.INVALID_CLUSTERING_RESULT,
                    sampleCount
            );
        } catch (RuntimeException _exception) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    sampleCount
            );
        }

        ClusteringRunSnapshot success;
        try {
            success = persistenceService.persistSuccess(
                    running.runId(),
                    validatedResult
            );
        } catch (ClusteringRunStateException exception) {
            return recordFailure(
                    running,
                    mapPersistenceFailure(exception.getCode()),
                    sampleCount
            );
        } catch (RuntimeException _exception) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    sampleCount
            );
        }

        if (success.status() != ClusteringRunStatus.SUCCESS) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.RUN_STATE_CONFLICT,
                    sampleCount
            );
        }
        return ClusteringExecutionResult.success(
                success.runId(),
                success.version(),
                sampleCount,
                running.clusterCount()
        );
    }

    private ClusteringExecutionResult recordFailure(
            ClusteringRunSnapshot knownRun,
            ClusteringRunFailureCode failureCode,
            int sampleCount
    ) {
        try {
            FailureRecordingResult recording = failureService.markFailed(
                    knownRun.runId(),
                    failureCode
            );
            return mapFailureRecording(
                    knownRun,
                    failureCode,
                    sampleCount,
                    recording
            );
        } catch (RuntimeException _exception) {
            return failureNotRecorded(knownRun, failureCode, sampleCount);
        }
    }

    private static ClusteringExecutionResult mapFailureRecording(
            ClusteringRunSnapshot knownRun,
            ClusteringRunFailureCode failureCode,
            int sampleCount,
            FailureRecordingResult recording
    ) {
        if (recording == null) {
            return failureNotRecorded(knownRun, failureCode, sampleCount);
        }
        return switch (recording.outcome()) {
            case RECORDED, ALREADY_FAILED -> recording.run()
                    .filter(run -> run.status() == ClusteringRunStatus.FAILED)
                    .map(run -> ClusteringExecutionResult.runFailed(
                            run.runId(),
                            run.version(),
                            failureCode,
                            sampleCount,
                            knownRun.clusterCount()
                    ))
                    .orElseGet(() -> failureNotRecorded(
                            knownRun,
                            failureCode,
                            sampleCount
                    ));
            case RUN_NOT_FOUND -> failureNotRecorded(
                    knownRun,
                    failureCode,
                    sampleCount
            );
            case TERMINAL_SUCCESS_CONFLICT -> recording.run()
                    .filter(run -> run.status() == ClusteringRunStatus.SUCCESS)
                    .map(run -> ClusteringExecutionResult.terminalStateConflict(
                            run.runId(),
                            run.version(),
                            failureCode,
                            sampleCount,
                            knownRun.clusterCount()
                    ))
                    .orElseGet(() -> failureNotRecorded(
                            knownRun,
                            failureCode,
                            sampleCount
                    ));
        };
    }

    private static ClusteringExecutionResult failureNotRecorded(
            ClusteringRunSnapshot knownRun,
            ClusteringRunFailureCode failureCode,
            int sampleCount
    ) {
        return ClusteringExecutionResult.runFailureNotRecorded(
                knownRun.runId(),
                knownRun.version(),
                failureCode,
                sampleCount,
                knownRun.clusterCount()
        );
    }

    private static ClusteringRunFailureCode mapMarkRunningFailure(
            ClusteringRunStateCode code
    ) {
        return switch (code) {
            case RUN_NOT_FOUND, INVALID_STATE_TRANSITION, RUN_ALREADY_TERMINAL ->
                    ClusteringRunFailureCode.RUN_STATE_CONFLICT;
            case RESULT_PERSISTENCE_FAILED ->
                    ClusteringRunFailureCode.RESULT_PERSISTENCE_FAILED;
            default -> ClusteringRunFailureCode.INTERNAL_ERROR;
        };
    }

    private static ClusteringRunFailureCode mapPersistenceFailure(
            ClusteringRunStateCode code
    ) {
        return switch (code) {
            case RUN_RESULT_MISMATCH ->
                    ClusteringRunFailureCode.INVALID_CLUSTERING_RESULT;
            case USER_REFERENCE_MISSING ->
                    ClusteringRunFailureCode.USER_REFERENCE_MISSING;
            case RESULT_SERIALIZATION_FAILED ->
                    ClusteringRunFailureCode.RESULT_SERIALIZATION_FAILED;
            case RESULT_PERSISTENCE_FAILED ->
                    ClusteringRunFailureCode.RESULT_PERSISTENCE_FAILED;
            case RUN_NOT_FOUND,
                    INVALID_STATE_TRANSITION,
                    RUN_ALREADY_TERMINAL,
                    RUN_RESULTS_ALREADY_EXIST ->
                    ClusteringRunFailureCode.RUN_STATE_CONFLICT;
            default -> ClusteringRunFailureCode.INTERNAL_ERROR;
        };
    }
}
