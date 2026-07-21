package com.example.campusactivity.controller;

import com.example.campusactivity.dto.clustering.CommunityMembersPageResponse;
import com.example.campusactivity.service.clustering.CommunityClusteringQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/community-clustering/communities")
public class AdminCommunityMemberController {
    private final CommunityClusteringQueryService queryService;

    public AdminCommunityMemberController(
            CommunityClusteringQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @GetMapping("/{communityId}/members")
    public CommunityMembersPageResponse findMembers(
            @PathVariable("communityId") String communityId,
            @RequestParam(name = "page", defaultValue = "0") String page,
            @RequestParam(name = "size", defaultValue = "20") String size
    ) {
        return queryService.findCommunityMembers(communityId, page, size);
    }
}
