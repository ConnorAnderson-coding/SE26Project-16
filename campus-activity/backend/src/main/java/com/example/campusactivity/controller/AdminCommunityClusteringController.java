package com.example.campusactivity.controller;

import com.example.campusactivity.dto.clustering.ClusteringRunDetailResponse;
import com.example.campusactivity.dto.clustering.ClusteringRunPageResponse;
import com.example.campusactivity.service.clustering.CommunityClusteringQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/community-clustering/runs")
public class AdminCommunityClusteringController {
    private final CommunityClusteringQueryService queryService;

    public AdminCommunityClusteringController(
            CommunityClusteringQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @GetMapping
    public ClusteringRunPageResponse findRuns(
            @RequestParam(name = "page", defaultValue = "0") String page,
            @RequestParam(name = "size", defaultValue = "20") String size
    ) {
        return queryService.findRuns(page, size);
    }

    @GetMapping("/{runId}")
    public ClusteringRunDetailResponse findRun(
            @PathVariable("runId") String runId
    ) {
        return queryService.findRunById(runId);
    }
}
