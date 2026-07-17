package com.example.campusactivity.dto.clustering;

public record CurrentUserClusteringResponse(
        String runId,
        String version,
        CurrentUserMembershipResponse membership
) {
}
