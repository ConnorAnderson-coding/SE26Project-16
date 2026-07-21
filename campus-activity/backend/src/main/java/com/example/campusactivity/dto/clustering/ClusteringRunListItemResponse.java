package com.example.campusactivity.dto.clustering;

import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;

import java.time.Instant;

public record ClusteringRunListItemResponse(
        String runId,
        String version,
        ClusteringAlgorithm algorithm,
        int clusterCount,
        int randomState,
        ClusteringRunStatus status,
        Integer sampleCount,
        String featureSchemaVersion,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String createdBy
) {
}
