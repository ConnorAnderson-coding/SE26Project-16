package com.example.campusactivity.service.clustering;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@ConditionalOnProperty(
        prefix = "community-clustering.python",
        name = "enabled",
        havingValue = "true"
)
public class CommunityClusteringOrchestrator {
    private final CommunityClusteringSubmissionService submissionService;
    private final CommunityClusteringRunExecutor runExecutor;

    public CommunityClusteringOrchestrator(
            CommunityClusteringSubmissionService submissionService,
            CommunityClusteringRunExecutor runExecutor
    ) {
        this.submissionService = submissionService;
        this.runExecutor = runExecutor;
    }

    public ClusteringExecutionResult execute(
            CommunityClusteringExecutionCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("聚类执行命令不能为空");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return ClusteringExecutionResult.preRunFailed(
                    ClusteringRunFailureCode.INTERNAL_ERROR,
                    0,
                    command.clusterCount()
            );
        }

        ClusteringSubmissionResult submission;
        try {
            submission = submissionService.submit(
                    command.clusterCount(),
                    command.createdBy()
            );
        } catch (ClusteringSubmissionException exception) {
            return switch (exception.getCode()) {
                case NO_EFFECTIVE_USERS, INVALID_CLUSTER_COUNT, ACTIVE_RUN_EXISTS ->
                        ClusteringExecutionResult.preconditionRejected(
                                exception.getCode(),
                                exception.getSampleCount(),
                                exception.getClusterCount()
                        );
                default -> ClusteringExecutionResult.preRunFailed(
                        exception.getCode(),
                        exception.getSampleCount(),
                        exception.getClusterCount()
                );
            };
        }

        ClusteringRunExecutionResult executed = runExecutor.executePendingRun(
                submission.runId()
        );
        return switch (executed.outcome()) {
            case SUCCESS -> ClusteringExecutionResult.success(
                    executed.runId(),
                    executed.version(),
                    executed.sampleCount(),
                    executed.clusterCount()
            );
            case RUN_FAILED -> ClusteringExecutionResult.runFailed(
                    executed.runId(),
                    executed.version(),
                    executed.failureCode(),
                    executed.sampleCount(),
                    executed.clusterCount()
            );
            case RUN_FAILURE_NOT_RECORDED, NOT_EXECUTABLE ->
                    ClusteringExecutionResult.runFailureNotRecorded(
                            executed.runId(),
                            executed.version(),
                            executed.failureCode(),
                            executed.sampleCount(),
                            executed.clusterCount()
                    );
            case TERMINAL_STATE_CONFLICT ->
                    ClusteringExecutionResult.terminalStateConflict(
                            executed.runId(),
                            executed.version(),
                            executed.failureCode(),
                            executed.sampleCount(),
                            executed.clusterCount()
                    );
        };
    }
}
