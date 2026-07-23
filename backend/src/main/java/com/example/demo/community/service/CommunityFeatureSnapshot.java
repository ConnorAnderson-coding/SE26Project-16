package com.example.demo.community.service;

import com.example.demo.community.client.ClusteringContracts.FeatureSample;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CommunityFeatureSnapshot(
        String schemaVersion,
        LocalDateTime windowStart,
        List<FeatureSample> samples,
        Map<String, Object> manifest,
        int featureDimension
) {
    public CommunityFeatureSnapshot {
        samples = List.copyOf(samples);
        manifest = Map.copyOf(manifest);
    }
}
