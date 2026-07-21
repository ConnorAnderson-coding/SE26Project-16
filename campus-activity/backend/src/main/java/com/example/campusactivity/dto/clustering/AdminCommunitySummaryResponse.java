package com.example.campusactivity.dto.clustering;

public record AdminCommunitySummaryResponse(
        String communityId,
        String runId,
        int clusterNo,
        String name,
        String color,
        int memberCount
) {
}
