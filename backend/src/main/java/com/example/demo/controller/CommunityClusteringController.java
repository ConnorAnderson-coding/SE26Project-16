package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.community.api.ClusteringApiException;
import com.example.demo.community.api.CommunityClusteringDtos.LatestResult;
import com.example.demo.community.api.CommunityClusteringDtos.MemberPage;
import com.example.demo.community.api.CommunityClusteringDtos.MyCommunity;
import com.example.demo.community.api.CommunityClusteringDtos.PageResponse;
import com.example.demo.community.api.CommunityClusteringDtos.RunAccepted;
import com.example.demo.community.api.CommunityClusteringDtos.RunDetail;
import com.example.demo.community.api.CommunityClusteringDtos.RunRequest;
import com.example.demo.community.api.CommunityClusteringDtos.RunSummary;
import com.example.demo.community.service.ClusteringStateException;
import com.example.demo.community.service.ClusteringSubmissionService;
import com.example.demo.community.service.CommunityClusteringQueryService;
import com.example.demo.config.ClusteringServiceProperties;
import com.example.demo.entity.ClusteringRun;
import com.example.demo.util.SecurityUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1")
public class CommunityClusteringController {

    private final CommunityClusteringQueryService queryService;
    private final ClusteringSubmissionService submissionService;
    private final ClusteringServiceProperties properties;
    private final ObjectReader runRequestReader;

    public CommunityClusteringController(
            CommunityClusteringQueryService queryService,
            ClusteringSubmissionService submissionService,
            ClusteringServiceProperties properties,
            ObjectMapper objectMapper
    ) {
        this.queryService = queryService;
        this.submissionService = submissionService;
        this.properties = properties;
        this.runRequestReader = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .readerFor(RunRequest.class);
    }

    @PostMapping("/admin/community-clustering/runs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RunAccepted>> submit(@RequestBody(required = false) String requestBody) {
        RunRequest request = parseRunRequest(requestBody);
        if (!properties.enabled()) {
            throw new ClusteringApiException(HttpStatus.SERVICE_UNAVAILABLE, "社区聚类服务当前未启用");
        }
        int clusterCount = request == null || request.clusterCount() == null ? 2 : request.clusterCount();
        try {
            ClusteringRun run = submissionService.submit(SecurityUtils.getCurrentUserId(), clusterCount);
            RunAccepted body = new RunAccepted(run.getId(), run.getVersion(), run.getAlgorithm().name(),
                    run.getClusterCount(), run.getRandomState(), run.getStatus().name(), run.getCreatedAt());
            return ResponseEntity.accepted()
                    .location(URI.create("/api/v1/admin/community-clustering/runs/" + run.getId()))
                    .body(ApiResponse.ok(body));
        } catch (ClusteringStateException exception) {
            throw mapStateException(exception);
        }
    }

    @GetMapping("/admin/community-clustering/runs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<RunSummary>> runs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.runs(page, size));
    }

    @GetMapping("/admin/community-clustering/runs/{runId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RunDetail> run(@PathVariable String runId) {
        return ApiResponse.ok(queryService.run(runId));
    }

    @GetMapping("/admin/community-clustering/communities/{communityId}/members")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MemberPage> members(
            @PathVariable String communityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.members(communityId, page, size));
    }

    @GetMapping("/community-clustering/latest")
    public ApiResponse<LatestResult> latest() {
        return ApiResponse.ok(queryService.latest(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/community-clustering/me")
    public ApiResponse<MyCommunity> mine() {
        return ApiResponse.ok(queryService.mine(SecurityUtils.getCurrentUserId()));
    }

    private static ClusteringApiException mapStateException(ClusteringStateException exception) {
        return switch (exception.getCode()) {
            case ACTIVE_RUN_EXISTS -> new ClusteringApiException(HttpStatus.CONFLICT, "已有聚类任务正在执行");
            case INVALID_PARAMETERS -> new ClusteringApiException(HttpStatus.BAD_REQUEST, "聚类参数或有效用户数量无效");
            default -> new ClusteringApiException(HttpStatus.INTERNAL_SERVER_ERROR, "聚类任务提交失败");
        };
    }

    private RunRequest parseRunRequest(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            return new RunRequest(null);
        }
        try {
            return runRequestReader.readValue(requestBody);
        } catch (IOException | RuntimeException exception) {
            throw new ClusteringApiException(HttpStatus.BAD_REQUEST, "聚类请求格式无效");
        }
    }
}
