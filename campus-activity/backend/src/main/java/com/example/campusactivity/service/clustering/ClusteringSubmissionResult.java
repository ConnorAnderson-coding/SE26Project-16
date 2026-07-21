package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;

import java.time.Instant;

public record ClusteringSubmissionResult(
        String runId,
        String version,
        ClusteringAlgorithm algorithm,
        int clusterCount,
        int randomState,
        ClusteringRunStatus status,
        Instant createdAt
) {
    static ClusteringSubmissionResult from(ClusteringRunSnapshot run) {
        return new ClusteringSubmissionResult(
                run.runId(),
                run.version(),
                run.algorithm(),
                run.clusterCount(),
                run.randomState(),
                run.status(),
                run.createdAt()
        );
    }
}
