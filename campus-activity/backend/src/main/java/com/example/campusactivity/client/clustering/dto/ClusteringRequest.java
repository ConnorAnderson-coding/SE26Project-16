package com.example.campusactivity.client.clustering.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ClusteringRequest(
        String runId,
        String version,
        String algorithm,
        Integer clusterCount,
        Integer randomState,
        String featureSchemaVersion,
        List<FeatureSample> samples
) {
    public ClusteringRequest {
        runId = ClusteringContractChecks.identifier(runId, "runId");
        version = ClusteringContractChecks.identifier(version, "version");
        if (!"KMEANS".equals(ClusteringContractChecks.string(algorithm, "algorithm"))) {
            throw new IllegalArgumentException("algorithm 必须为 KMEANS");
        }
        int checkedClusterCount = ClusteringContractChecks.requiredInteger(clusterCount, "clusterCount");
        if (ClusteringContractChecks.requiredInteger(randomState, "randomState") != 42) {
            throw new IllegalArgumentException("randomState 必须为 42");
        }
        if (!"community-features-v1".equals(
                ClusteringContractChecks.string(featureSchemaVersion, "featureSchemaVersion")
        )) {
            throw new IllegalArgumentException("featureSchemaVersion 必须为 community-features-v1");
        }
        Objects.requireNonNull(samples, "samples 不能为空");
        samples = List.copyOf(samples);
        if (samples.size() < 2) {
            throw new IllegalArgumentException("samples 至少需要 2 条");
        }
        if (checkedClusterCount < 2 || checkedClusterCount > samples.size()) {
            throw new IllegalArgumentException("clusterCount 必须在 2 和 samples 数量之间");
        }
        Set<String> userIds = new HashSet<>();
        for (FeatureSample sample : samples) {
            if (!userIds.add(sample.userId())) {
                throw new IllegalArgumentException("samples 中的 userId 不能重复");
            }
        }
    }
}
