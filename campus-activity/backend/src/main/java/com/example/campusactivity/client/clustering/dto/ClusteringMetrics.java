package com.example.campusactivity.client.clustering.dto;

import java.util.List;
import java.util.Objects;

public record ClusteringMetrics(
        Double inertia,
        List<Double> pcaExplainedVarianceRatio
) {
    public ClusteringMetrics {
        double checkedInertia = ClusteringContractChecks.finite(inertia, "metrics.inertia");
        if (checkedInertia < 0.0) {
            throw new IllegalArgumentException("metrics.inertia 不能为负数");
        }
        Objects.requireNonNull(pcaExplainedVarianceRatio, "metrics.pcaExplainedVarianceRatio 不能为空");
        pcaExplainedVarianceRatio = List.copyOf(pcaExplainedVarianceRatio);
        if (pcaExplainedVarianceRatio.size() != 2) {
            throw new IllegalArgumentException("metrics.pcaExplainedVarianceRatio 长度必须为 2");
        }
        for (Double ratio : pcaExplainedVarianceRatio) {
            double checkedRatio = ClusteringContractChecks.finite(
                    ratio,
                    "metrics.pcaExplainedVarianceRatio"
            );
            if (checkedRatio < 0.0 || checkedRatio > 1.0) {
                throw new IllegalArgumentException("PCA 解释方差比例必须在 0 到 1 之间");
            }
        }
    }
}
