package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.ClusteringMetrics;
import com.example.campusactivity.client.clustering.dto.CommunitySummary;
import com.example.campusactivity.client.clustering.dto.MemberResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record ValidatedClusteringResult(
        String runId,
        String version,
        String algorithm,
        int clusterCount,
        int sampleCount,
        ClusteringMetrics metrics,
        List<CommunitySummary> communities,
        List<MemberResult> members,
        Map<Integer, List<MemberResult>> membersByClusterNo
) {
    public ValidatedClusteringResult {
        Objects.requireNonNull(runId, "runId 不能为空");
        Objects.requireNonNull(version, "version 不能为空");
        Objects.requireNonNull(algorithm, "algorithm 不能为空");
        Objects.requireNonNull(metrics, "metrics 不能为空");
        communities = List.copyOf(Objects.requireNonNull(communities, "communities 不能为空"));
        members = List.copyOf(Objects.requireNonNull(members, "members 不能为空"));

        Objects.requireNonNull(membersByClusterNo, "membersByClusterNo 不能为空");
        Map<Integer, List<MemberResult>> sortedGroups = new TreeMap<>();
        for (Map.Entry<Integer, List<MemberResult>> entry : membersByClusterNo.entrySet()) {
            Integer clusterNo = Objects.requireNonNull(entry.getKey(), "clusterNo 不能为空");
            List<MemberResult> group = List.copyOf(
                    Objects.requireNonNull(entry.getValue(), "成员列表不能为空")
            );
            sortedGroups.put(clusterNo, group);
        }
        membersByClusterNo = Collections.unmodifiableMap(new LinkedHashMap<>(sortedGroups));
    }
}
