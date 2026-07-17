package com.example.campusactivity.dto.clustering;

public record CurrentUserMembershipResponse(
        String communityId,
        int clusterNo,
        String communityName,
        String color,
        String pointId,
        double x,
        double y,
        double distanceToCenter
) {
}
