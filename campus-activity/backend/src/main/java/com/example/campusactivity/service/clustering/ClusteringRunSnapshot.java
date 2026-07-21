package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;

import java.time.Instant;

public record ClusteringRunSnapshot(
        String runId,
        String version,
        ClusteringAlgorithm algorithm,
        Integer clusterCount,
        Integer randomState,
        ClusteringRunStatus status,
        Integer sampleCount,
        String featureSchemaVersion,
        String parametersJson,
        String metricsJson,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        String createdBy,
        Instant createdAt
) {
    static ClusteringRunSnapshot from(ClusteringRun run) {
        return new ClusteringRunSnapshot(
                run.getId(),
                run.getVersion(),
                run.getAlgorithm(),
                run.getClusterCount(),
                run.getRandomState(),
                run.getStatus(),
                run.getSampleCount(),
                run.getFeatureSchemaVersion(),
                run.getParametersJson(),
                run.getMetricsJson(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getErrorMessage(),
                run.getCreatedBy(),
                run.getCreatedAt()
        );
    }
}
