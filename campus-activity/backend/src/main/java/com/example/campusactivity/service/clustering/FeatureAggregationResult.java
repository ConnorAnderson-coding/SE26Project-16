package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.FeatureSample;

import java.util.List;
import java.util.Objects;

public record FeatureAggregationResult(
        List<FeatureSample> samples,
        List<ExcludedUserDiagnostic> excludedUsers,
        FeatureAggregationDiagnostics diagnostics
) {
    public FeatureAggregationResult {
        samples = List.copyOf(Objects.requireNonNull(samples, "samples 不能为空"));
        excludedUsers = List.copyOf(Objects.requireNonNull(excludedUsers, "excludedUsers 不能为空"));
        Objects.requireNonNull(diagnostics, "diagnostics 不能为空");
    }
}
