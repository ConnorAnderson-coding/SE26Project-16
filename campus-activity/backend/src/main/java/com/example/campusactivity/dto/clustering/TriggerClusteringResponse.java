package com.example.campusactivity.dto.clustering;

import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.service.clustering.ClusteringSubmissionResult;

import java.time.Instant;

public record TriggerClusteringResponse(
        String runId,
        String version,
        ClusteringAlgorithm algorithm,
        int clusterCount,
        int randomState,
        ClusteringRunStatus status,
        Instant createdAt
) {
    public static TriggerClusteringResponse from(ClusteringSubmissionResult result) {
        return new TriggerClusteringResponse(
                result.runId(),
                result.version(),
                result.algorithm(),
                result.clusterCount(),
                result.randomState(),
                result.status(),
                result.createdAt()
        );
    }
}
