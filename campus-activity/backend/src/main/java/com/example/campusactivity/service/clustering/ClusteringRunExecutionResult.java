package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringRunStatus;

public record ClusteringRunExecutionResult(
        ClusteringRunExecutionOutcome outcome,
        String runId,
        String version,
        ClusteringRunStatus finalStatus,
        ClusteringRunFailureCode failureCode,
        boolean failureRecorded,
        int sampleCount,
        int clusterCount
) {
    static ClusteringRunExecutionResult success(ClusteringRunSnapshot run, int sampleCount) {
        return new ClusteringRunExecutionResult(
                ClusteringRunExecutionOutcome.SUCCESS,
                run.runId(), run.version(), ClusteringRunStatus.SUCCESS,
                null, false, sampleCount, run.clusterCount()
        );
    }

    static ClusteringRunExecutionResult notExecutable(
            ClusteringRunSnapshot run,
            ClusteringRunFailureCode failureCode
    ) {
        return new ClusteringRunExecutionResult(
                ClusteringRunExecutionOutcome.NOT_EXECUTABLE,
                run == null ? null : run.runId(),
                run == null ? null : run.version(),
                run == null ? null : run.status(),
                failureCode,
                false,
                run == null || run.sampleCount() == null ? 0 : run.sampleCount(),
                run == null || run.clusterCount() == null ? 0 : run.clusterCount()
        );
    }

    static ClusteringRunExecutionResult runFailed(
            ClusteringRunSnapshot run,
            ClusteringRunFailureCode failureCode,
            int sampleCount
    ) {
        return new ClusteringRunExecutionResult(
                ClusteringRunExecutionOutcome.RUN_FAILED,
                run.runId(), run.version(), ClusteringRunStatus.FAILED,
                failureCode, true, sampleCount, run.clusterCount()
        );
    }

    static ClusteringRunExecutionResult failureNotRecorded(
            ClusteringRunSnapshot run,
            ClusteringRunFailureCode failureCode,
            int sampleCount
    ) {
        return new ClusteringRunExecutionResult(
                ClusteringRunExecutionOutcome.RUN_FAILURE_NOT_RECORDED,
                run.runId(), run.version(), null,
                failureCode, false, sampleCount, run.clusterCount()
        );
    }

    static ClusteringRunExecutionResult terminalConflict(
            ClusteringRunSnapshot run,
            ClusteringRunFailureCode failureCode,
            int sampleCount
    ) {
        return new ClusteringRunExecutionResult(
                ClusteringRunExecutionOutcome.TERMINAL_STATE_CONFLICT,
                run.runId(), run.version(), ClusteringRunStatus.SUCCESS,
                failureCode, false, sampleCount, run.clusterCount()
        );
    }
}
