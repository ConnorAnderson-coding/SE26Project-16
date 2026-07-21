package com.example.campusactivity.service.clustering;

public final class ClusteringServiceDisabledException extends RuntimeException {
    public ClusteringServiceDisabledException() {
        super("CLUSTERING_SERVICE_UNAVAILABLE");
    }
}
