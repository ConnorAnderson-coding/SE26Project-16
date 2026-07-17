package com.example.campusactivity.dto.clustering;

import java.util.List;

public record LatestClusteringResponse(
        ClusteringRunSummaryResponse run,
        List<CommunityResponse> communities
) {
    public LatestClusteringResponse {
        communities = List.copyOf(communities);
    }
}
