package com.example.campusactivity.dto.clustering;

public record AdminCommunityMemberResponse(
        String userId,
        String name,
        String college,
        String grade,
        String pointId,
        double x,
        double y,
        double distanceToCenter
) {
}
