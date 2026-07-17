package com.example.campusactivity.dto.clustering;

import java.util.List;

public record ClusteringMetricsResponse(
        double inertia,
        List<Double> pcaExplainedVarianceRatio
) {
    public ClusteringMetricsResponse {
        pcaExplainedVarianceRatio = List.copyOf(pcaExplainedVarianceRatio);
    }
}
