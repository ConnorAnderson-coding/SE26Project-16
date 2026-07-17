package com.example.campusactivity.dto.clustering;

public record CommunityMemberPointResponse(
        String pointId,
        double x,
        double y,
        boolean currentUser
) {
}
