package com.example.campusactivity.repository.projection;

import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;

import java.time.Instant;

public interface ClusteringRunQueryProjection {
    String getId();

    String getVersion();

    ClusteringAlgorithm getAlgorithm();

    Integer getClusterCount();

    Integer getRandomState();

    ClusteringRunStatus getStatus();

    Integer getSampleCount();

    String getFeatureSchemaVersion();

    String getMetricsJson();

    String getErrorMessage();

    Instant getCreatedAt();

    Instant getStartedAt();

    Instant getFinishedAt();

    String getCreatedBy();
}
