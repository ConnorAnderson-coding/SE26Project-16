package com.example.campusactivity.dto.clustering;

import java.util.List;

public record CommunityResponse(
        String communityId,
        int clusterNo,
        String name,
        String description,
        int memberCount,
        List<String> topInterests,
        String color,
        List<CommunityMemberPointResponse> points
) {
    public CommunityResponse {
        topInterests = List.copyOf(topInterests);
        points = List.copyOf(points);
    }
}
