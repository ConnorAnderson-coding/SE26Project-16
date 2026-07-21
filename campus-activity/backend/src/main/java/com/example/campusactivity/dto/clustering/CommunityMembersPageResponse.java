package com.example.campusactivity.dto.clustering;

import java.util.List;

public record CommunityMembersPageResponse(
        AdminCommunitySummaryResponse community,
        List<AdminCommunityMemberResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public CommunityMembersPageResponse {
        items = List.copyOf(items);
    }
}
