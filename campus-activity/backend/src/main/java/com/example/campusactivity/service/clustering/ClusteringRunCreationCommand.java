package com.example.campusactivity.service.clustering;

public record ClusteringRunCreationCommand(
        Integer clusterCount,
        Integer sampleCount,
        String createdBy
) {
}
