package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringRunStatus;

import java.util.Objects;

public record ClusteringExecutionResult(
        ClusteringExecutionOutcome outcome,
        String runId,
        String version,
        ClusteringRunStatus finalStatus,
        ClusteringRunFailureCode failureCode,
        boolean failureRecorded,
        int sampleCount,
        int clusterCount
) {
    public ClusteringExecutionResult {
        Objects.requireNonNull(outcome, "outcome");
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount 不能为负数");
        }
        if (clusterCount < 2) {
            throw new IllegalArgumentException("clusterCount 必须至少为2");
        }

        switch (outcome) {
            case SUCCESS -> {
                requireRunIdentity(runId, version);
                requireState(finalStatus, ClusteringRunStatus.SUCCESS);
                requireNullFailure(failureCode, failureRecorded);
            }
            case PRECONDITION_REJECTED, PRE_RUN_FAILED -> {
                requireNoRunIdentity(runId, version, finalStatus);
                requireFailure(failureCode, failureRecorded, false);
            }
            case RUN_FAILED -> {
                requireRunIdentity(runId, version);
                requireState(finalStatus, ClusteringRunStatus.FAILED);
                requireFailure(failureCode, failureRecorded, true);
            }
            case RUN_FAILURE_NOT_RECORDED -> {
                requireOptionalRunIdentity(runId, version);
                if (finalStatus != null) {
                    throw new IllegalArgumentException("未记录失败不能声明最终状态");
                }
                requireFailure(failureCode, failureRecorded, false);
            }
            case TERMINAL_STATE_CONFLICT -> {
                requireRunIdentity(runId, version);
                requireState(finalStatus, ClusteringRunStatus.SUCCESS);
                requireFailure(failureCode, failureRecorded, false);
            }
        }
    }

    public static ClusteringExecutionResult success(
            String runId,
            String version,
            int sampleCount,
            int clusterCount
    ) {
        return new ClusteringExecutionResult(
                ClusteringExecutionOutcome.SUCCESS,
                runId,
                version,
                ClusteringRunStatus.SUCCESS,
                null,
                false,
                sampleCount,
                clusterCount
        );
    }

    public static ClusteringExecutionResult preconditionRejected(
            ClusteringRunFailureCode failureCode,
            int sampleCount,
            int clusterCount
    ) {
        return withoutRun(
                ClusteringExecutionOutcome.PRECONDITION_REJECTED,
                failureCode,
                sampleCount,
                clusterCount
        );
    }

    public static ClusteringExecutionResult preRunFailed(
            ClusteringRunFailureCode failureCode,
            int sampleCount,
            int clusterCount
    ) {
        return withoutRun(
                ClusteringExecutionOutcome.PRE_RUN_FAILED,
                failureCode,
                sampleCount,
                clusterCount
        );
    }

    public static ClusteringExecutionResult runFailed(
            String runId,
            String version,
            ClusteringRunFailureCode failureCode,
            int sampleCount,
            int clusterCount
    ) {
        return new ClusteringExecutionResult(
                ClusteringExecutionOutcome.RUN_FAILED,
                runId,
                version,
                ClusteringRunStatus.FAILED,
                failureCode,
                true,
                sampleCount,
                clusterCount
        );
    }

    public static ClusteringExecutionResult runFailureNotRecorded(
            String runId,
            String version,
            ClusteringRunFailureCode failureCode,
            int sampleCount,
            int clusterCount
    ) {
        return new ClusteringExecutionResult(
                ClusteringExecutionOutcome.RUN_FAILURE_NOT_RECORDED,
                runId,
                version,
                null,
                failureCode,
                false,
                sampleCount,
                clusterCount
        );
    }

    public static ClusteringExecutionResult terminalStateConflict(
            String runId,
            String version,
            ClusteringRunFailureCode failureCode,
            int sampleCount,
            int clusterCount
    ) {
        return new ClusteringExecutionResult(
                ClusteringExecutionOutcome.TERMINAL_STATE_CONFLICT,
                runId,
                version,
                ClusteringRunStatus.SUCCESS,
                failureCode,
                false,
                sampleCount,
                clusterCount
        );
    }

    private static ClusteringExecutionResult withoutRun(
            ClusteringExecutionOutcome outcome,
            ClusteringRunFailureCode failureCode,
            int sampleCount,
            int clusterCount
    ) {
        return new ClusteringExecutionResult(
                outcome,
                null,
                null,
                null,
                failureCode,
                false,
                sampleCount,
                clusterCount
        );
    }

    private static void requireRunIdentity(String runId, String version) {
        requireIdentifier(runId, "runId");
        requireIdentifier(version, "version");
    }

    private static void requireOptionalRunIdentity(String runId, String version) {
        if ((runId == null) != (version == null)) {
            throw new IllegalArgumentException("runId和version必须同时存在或同时为空");
        }
        if (runId != null) {
            requireRunIdentity(runId, version);
        }
    }

    private static void requireNoRunIdentity(
            String runId,
            String version,
            ClusteringRunStatus finalStatus
    ) {
        if (runId != null || version != null || finalStatus != null) {
            throw new IllegalArgumentException("运行创建前结果不能包含运行标识或状态");
        }
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException(field + " 不符合运行标识约束");
        }
    }

    private static void requireState(
            ClusteringRunStatus actual,
            ClusteringRunStatus expected
    ) {
        if (actual != expected) {
            throw new IllegalArgumentException("最终状态与执行结果不一致");
        }
    }

    private static void requireNullFailure(
            ClusteringRunFailureCode failureCode,
            boolean failureRecorded
    ) {
        if (failureCode != null || failureRecorded) {
            throw new IllegalArgumentException("成功结果不能包含失败信息");
        }
    }

    private static void requireFailure(
            ClusteringRunFailureCode failureCode,
            boolean actualRecorded,
            boolean expectedRecorded
    ) {
        Objects.requireNonNull(failureCode, "failureCode");
        if (actualRecorded != expectedRecorded) {
            throw new IllegalArgumentException("失败记录标志与执行结果不一致");
        }
    }
}
