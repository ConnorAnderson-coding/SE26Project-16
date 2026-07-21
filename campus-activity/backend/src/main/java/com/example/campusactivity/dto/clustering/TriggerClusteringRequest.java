package com.example.campusactivity.dto.clustering;

public record TriggerClusteringRequest(Integer clusterCount) {
    public static final int DEFAULT_CLUSTER_COUNT = 2;

    public int resolvedClusterCount() {
        return clusterCount == null ? DEFAULT_CLUSTER_COUNT : clusterCount;
    }
}
