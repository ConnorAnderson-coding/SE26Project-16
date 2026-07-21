package com.example.campusactivity.controller;

import com.example.campusactivity.dto.clustering.CurrentUserClusteringResponse;
import com.example.campusactivity.dto.clustering.LatestClusteringResponse;
import com.example.campusactivity.service.clustering.CommunityClusteringQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community-clustering")
public class CommunityClusteringController {
    private final CommunityClusteringQueryService queryService;

    public CommunityClusteringController(
            CommunityClusteringQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @GetMapping("/latest")
    public LatestClusteringResponse latest(Authentication authentication) {
        return queryService.findLatestClustering(authentication.getName());
    }

    @GetMapping("/me")
    public CurrentUserClusteringResponse me(Authentication authentication) {
        return queryService.findCurrentUserClustering(authentication.getName());
    }
}
