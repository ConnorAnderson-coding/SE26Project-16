package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.client.clustering.dto.ClusteringRequest;
import com.example.campusactivity.client.clustering.dto.ClusteringResponse;
import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.client.clustering.exception.ClusteringClientException;
import com.example.campusactivity.client.clustering.exception.ClusteringRemoteException;
import com.example.campusactivity.client.clustering.exception.ClusteringServiceUnavailableException;
import com.example.campusactivity.client.clustering.exception.InvalidClusteringServiceResponseException;
import com.example.campusactivity.entity.ClusteringRunInput;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.repository.ClusteringRunInputRepository;
import com.example.campusactivity.repository.ClusteringRunRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnProperty(
        prefix = "community-clustering.python",
        name = "enabled",
        havingValue = "true"
)
public class CommunityClusteringRunExecutor {
    private final ClusteringRunLifecycleService lifecycleService;
    private final ClusteringRunRepository runRepository;
    private final ClusteringRunInputRepository inputRepository;
    private final ClusteringRunInputCodec inputCodec;
    private final ClusteringRunFailureService failureService;
    private final ClusteringClient clusteringClient;
    private final ClusteringResponseValidator responseValidator;
    private final ClusteringResultPersistenceService persistenceService;

    public CommunityClusteringRunExecutor(
            ClusteringRunLifecycleService lifecycleService,
            ClusteringRunRepository runRepository,
            ClusteringRunInputRepository inputRepository,
            ClusteringRunInputCodec inputCodec,
            ClusteringRunFailureService failureService,
            ClusteringClient clusteringClient,
            ClusteringResponseValidator responseValidator,
            ClusteringResultPersistenceService persistenceService
    ) {
        this.lifecycleService = lifecycleService;
        this.runRepository = runRepository;
        this.inputRepository = inputRepository;
        this.inputCodec = inputCodec;
        this.failureService = failureService;
        this.clusteringClient = clusteringClient;
        this.responseValidator = responseValidator;
        this.persistenceService = persistenceService;
    }

    public ClusteringRunExecutionResult executePendingRun(String runId) {
        ClusteringRunSnapshot known = findSnapshot(runId);
        if (known == null) {
            return ClusteringRunExecutionResult.notExecutable(
                    null,
                    ClusteringRunFailureCode.RUN_STATE_CONFLICT
            );
        }

        ClusteringRunSnapshot running;
        try {
            running = lifecycleService.markRunning(runId);
        } catch (ClusteringRunStateException exception) {
            if (known.status() != ClusteringRunStatus.PENDING) {
                return ClusteringRunExecutionResult.notExecutable(
                        known,
                        ClusteringRunFailureCode.RUN_STATE_CONFLICT
                );
            }
            return recordFailure(
                    known,
                    mapMarkRunningFailure(exception.getCode()),
                    sampleCount(known)
            );
        } catch (RuntimeException exception) {
            return recordFailure(
                    known,
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    sampleCount(known)
            );
        }
        return executeRunningSnapshot(running);
    }

    public ClusteringRunExecutionResult executeClaimedRun(String runId) {
        ClusteringRunSnapshot running = findSnapshot(runId);
        if (running == null || running.status() != ClusteringRunStatus.RUNNING) {
            return ClusteringRunExecutionResult.notExecutable(
                    running,
                    ClusteringRunFailureCode.RUN_STATE_CONFLICT
            );
        }
        return executeRunningSnapshot(running);
    }

    private ClusteringRunExecutionResult executeRunningSnapshot(
            ClusteringRunSnapshot running
    ) {
        int expectedSampleCount = sampleCount(running);
        List<FeatureSample> samples;
        try {
            samples = loadInputs(running, expectedSampleCount);
        } catch (RuntimeException exception) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.INPUT_SNAPSHOT_INVALID,
                    expectedSampleCount
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
        } catch (RuntimeException exception) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.INPUT_SNAPSHOT_INVALID,
                    expectedSampleCount
            );
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return recordFailure(
                    running,
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    expectedSampleCount
            );
        }

        ClusteringResponse response;
        try {
            response = clusteringClient.runClustering(request);
        } catch (ClusteringRemoteException exception) {
            return recordFailure(running, ClusteringRunFailureCode.PYTHON_REQUEST_REJECTED, expectedSampleCount);
        } catch (ClusteringServiceUnavailableException exception) {
            return recordFailure(running, ClusteringRunFailureCode.PYTHON_SERVICE_UNAVAILABLE, expectedSampleCount);
        } catch (InvalidClusteringServiceResponseException exception) {
            return recordFailure(running, ClusteringRunFailureCode.PYTHON_PROTOCOL_ERROR, expectedSampleCount);
        } catch (ClusteringClientException exception) {
            return recordFailure(running, ClusteringRunFailureCode.INTERNAL_ERROR, expectedSampleCount);
        } catch (RuntimeException exception) {
            return recordFailure(running, ClusteringRunFailureCode.INTERNAL_ERROR, expectedSampleCount);
        }

        ValidatedClusteringResult validated;
        try {
            validated = responseValidator.validate(request, response);
        } catch (ClusteringResponseValidationException exception) {
            return recordFailure(running, ClusteringRunFailureCode.INVALID_CLUSTERING_RESULT, expectedSampleCount);
        } catch (RuntimeException exception) {
            return recordFailure(running, ClusteringRunFailureCode.INTERNAL_ERROR, expectedSampleCount);
        }

        try {
            ClusteringRunSnapshot success = persistenceService.persistSuccess(
                    running.runId(),
                    validated
            );
            if (success.status() != ClusteringRunStatus.SUCCESS) {
                return recordFailure(running, ClusteringRunFailureCode.RUN_STATE_CONFLICT, expectedSampleCount);
            }
            return ClusteringRunExecutionResult.success(success, expectedSampleCount);
        } catch (ClusteringRunStateException exception) {
            return recordFailure(
                    running,
                    mapPersistenceFailure(exception.getCode()),
                    expectedSampleCount
            );
        } catch (RuntimeException exception) {
            return recordFailure(running, ClusteringRunFailureCode.INTERNAL_ERROR, expectedSampleCount);
        }
    }

    private List<FeatureSample> loadInputs(
            ClusteringRunSnapshot running,
            int expectedSampleCount
    ) {
        List<ClusteringRunInput> inputs = inputRepository
                .findByRunIdOrderBySampleOrderAsc(running.runId());
        if (inputs.size() != expectedSampleCount) {
            throw new IllegalStateException("Unexpected input count");
        }

        Set<String> userIds = new HashSet<>();
        List<FeatureSample> samples = new ArrayList<>(inputs.size());
        for (int index = 0; index < inputs.size(); index++) {
            ClusteringRunInput input = inputs.get(index);
            if (input.getSampleOrder() == null
                    || input.getSampleOrder() != index
                    || !userIds.add(input.getUserId())) {
                throw new IllegalStateException("Invalid input sequence");
            }
            samples.add(inputCodec.decode(input));
        }
        return List.copyOf(samples);
    }

    private ClusteringRunSnapshot findSnapshot(String runId) {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        return runRepository.findById(runId)
                .map(ClusteringRunSnapshot::from)
                .orElse(null);
    }

    private ClusteringRunExecutionResult recordFailure(
            ClusteringRunSnapshot knownRun,
            ClusteringRunFailureCode failureCode,
            int sampleCount
    ) {
        try {
            FailureRecordingResult recording = failureService.markFailed(
                    knownRun.runId(),
                    failureCode
            );
            if (recording == null) {
                return ClusteringRunExecutionResult.failureNotRecorded(knownRun, failureCode, sampleCount);
            }
            return switch (recording.outcome()) {
                case RECORDED, ALREADY_FAILED -> recording.run()
                        .filter(run -> run.status() == ClusteringRunStatus.FAILED)
                        .map(run -> ClusteringRunExecutionResult.runFailed(run, failureCode, sampleCount))
                        .orElseGet(() -> ClusteringRunExecutionResult.failureNotRecorded(
                                knownRun, failureCode, sampleCount
                        ));
                case TERMINAL_SUCCESS_CONFLICT -> recording.run()
                        .filter(run -> run.status() == ClusteringRunStatus.SUCCESS)
                        .map(run -> ClusteringRunExecutionResult.terminalConflict(run, failureCode, sampleCount))
                        .orElseGet(() -> ClusteringRunExecutionResult.failureNotRecorded(
                                knownRun, failureCode, sampleCount
                        ));
                case RUN_NOT_FOUND -> ClusteringRunExecutionResult.failureNotRecorded(
                        knownRun, failureCode, sampleCount
                );
            };
        } catch (RuntimeException exception) {
            return ClusteringRunExecutionResult.failureNotRecorded(knownRun, failureCode, sampleCount);
        }
    }

    private static int sampleCount(ClusteringRunSnapshot run) {
        return run.sampleCount() == null ? 0 : run.sampleCount();
    }

    private static ClusteringRunFailureCode mapMarkRunningFailure(ClusteringRunStateCode code) {
        return code == ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED
                ? ClusteringRunFailureCode.RESULT_PERSISTENCE_FAILED
                : ClusteringRunFailureCode.RUN_STATE_CONFLICT;
    }

    private static ClusteringRunFailureCode mapPersistenceFailure(ClusteringRunStateCode code) {
        return switch (code) {
            case RUN_RESULT_MISMATCH -> ClusteringRunFailureCode.INVALID_CLUSTERING_RESULT;
            case USER_REFERENCE_MISSING -> ClusteringRunFailureCode.USER_REFERENCE_MISSING;
            case RESULT_SERIALIZATION_FAILED -> ClusteringRunFailureCode.RESULT_SERIALIZATION_FAILED;
            case RESULT_PERSISTENCE_FAILED -> ClusteringRunFailureCode.RESULT_PERSISTENCE_FAILED;
            case RUN_NOT_FOUND, INVALID_STATE_TRANSITION, RUN_ALREADY_TERMINAL,
                    RUN_RESULTS_ALREADY_EXIST -> ClusteringRunFailureCode.RUN_STATE_CONFLICT;
            default -> ClusteringRunFailureCode.INTERNAL_ERROR;
        };
    }
}
