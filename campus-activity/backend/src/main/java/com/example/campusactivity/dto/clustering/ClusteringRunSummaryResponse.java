package com.example.campusactivity.dto.clustering;

import com.example.campusactivity.entity.ClusteringAlgorithm;

import java.time.Instant;

public record ClusteringRunSummaryResponse(
        String runId,
        String version,
        ClusteringAlgorithm algorithm,
        int clusterCount,
        int sampleCount,
        Instant finishedAt
) {
}
