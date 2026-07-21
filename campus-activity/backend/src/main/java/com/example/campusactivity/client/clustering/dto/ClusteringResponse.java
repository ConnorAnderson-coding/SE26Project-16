package com.example.campusactivity.client.clustering.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ClusteringResponse(
        String runId,
        String version,
        String algorithm,
        Integer clusterCount,
        Integer sampleCount,
        ClusteringMetrics metrics,
        List<CommunitySummary> communities,
        List<MemberResult> members
) {
    public ClusteringResponse {
        runId = ClusteringContractChecks.identifier(runId, "runId");
        version = ClusteringContractChecks.identifier(version, "version");
        if (!"KMEANS".equals(ClusteringContractChecks.string(algorithm, "algorithm"))) {
            throw new IllegalArgumentException("algorithm 必须为 KMEANS");
        }
        if (ClusteringContractChecks.requiredInteger(clusterCount, "clusterCount") < 2) {
            throw new IllegalArgumentException("clusterCount 不能小于 2");
        }
        if (ClusteringContractChecks.requiredInteger(sampleCount, "sampleCount") < 2) {
            throw new IllegalArgumentException("sampleCount 不能小于 2");
        }
        metrics = Objects.requireNonNull(metrics, "metrics 不能为空");
        Objects.requireNonNull(communities, "communities 不能为空");
        communities = List.copyOf(communities);
        Objects.requireNonNull(members, "members 不能为空");
        members = List.copyOf(members);
        Set<String> memberUserIds = new HashSet<>();
        for (MemberResult member : members) {
            if (!memberUserIds.add(member.userId())) {
                throw new IllegalArgumentException("members 中的 userId 不能重复");
            }
        }
    }
}
