package com.example.campusactivity.dto.clustering;

import java.util.List;

public record ClusteringRunPageResponse(
        List<ClusteringRunListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public ClusteringRunPageResponse {
        items = List.copyOf(items);
    }
}
