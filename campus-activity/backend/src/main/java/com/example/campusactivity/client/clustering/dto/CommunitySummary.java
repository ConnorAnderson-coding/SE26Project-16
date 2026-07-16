package com.example.campusactivity.client.clustering.dto;

import java.util.List;

public record CommunitySummary(
        Integer clusterNo,
        Integer memberCount,
        List<String> topInterests
) {
    public CommunitySummary {
        ClusteringContractChecks.nonNegative(clusterNo, "communities[].clusterNo");
        int checkedMemberCount = ClusteringContractChecks.requiredInteger(
                memberCount,
                "communities[].memberCount"
        );
        if (checkedMemberCount <= 0) {
            throw new IllegalArgumentException("communities[].memberCount 必须为正整数");
        }
        topInterests = ClusteringContractChecks.stringList(
                topInterests,
                "communities[].topInterests",
                false
        );
        if (topInterests.size() > 3) {
            throw new IllegalArgumentException("communities[].topInterests 最多包含 3 项");
        }
    }
}
