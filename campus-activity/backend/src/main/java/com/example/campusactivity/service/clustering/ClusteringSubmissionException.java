package com.example.campusactivity.service.clustering;

public final class ClusteringSubmissionException extends RuntimeException {
    private final ClusteringRunFailureCode code;
    private final int sampleCount;
    private final int clusterCount;

    ClusteringSubmissionException(
            ClusteringRunFailureCode code,
            int sampleCount,
            int clusterCount
    ) {
        super(code.name());
        this.code = code;
        this.sampleCount = sampleCount;
        this.clusterCount = clusterCount;
    }

    public ClusteringRunFailureCode getCode() {
        return code;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public int getClusterCount() {
        return clusterCount;
    }
}
