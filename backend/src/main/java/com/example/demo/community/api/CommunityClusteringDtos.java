package com.example.demo.community.api;

import java.time.LocalDateTime;
import java.util.List;

public final class CommunityClusteringDtos {

    private CommunityClusteringDtos() {
    }

    public record RunRequest(Integer clusterCount) {
    }

    public record RunAccepted(
            String runId, String version, String algorithm, Integer clusterCount,
            Integer randomState, String status, LocalDateTime createdAt
    ) {
    }

    public record RunSummary(
            String runId, String version, String algorithm, Integer clusterCount,
            Integer randomState, String status, Integer sampleCount,
            String featureSchemaVersion, LocalDateTime createdAt, LocalDateTime startedAt,
            LocalDateTime finishedAt, String createdBy
    ) {
    }

    public record Metrics(Double inertia, List<Double> pcaExplainedVarianceRatio) {
    }

    public record Failure(String code, String message) {
    }

    public record RunDetail(
            String runId, String version, String algorithm, Integer clusterCount,
            Integer randomState, String status, Integer sampleCount, Integer featureDimension,
            String featureSchemaVersion, Metrics metrics, Failure failure,
            LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt,
            String createdBy
    ) {
    }

    public record PageResponse<T>(
            List<T> items, int page, int size, long totalElements, int totalPages
    ) {
    }

    public record LatestRun(
            String runId, String version, String algorithm, Integer clusterCount,
            Integer sampleCount, LocalDateTime finishedAt
    ) {
    }

    public record Point(String pointId, Double x, Double y, boolean currentUser) {
    }

    public record CommunityView(
            String communityId, Integer clusterNo, String name, String description,
            Integer memberCount, List<String> topInterests, String color, List<Point> points
    ) {
    }

    public record LatestResult(LatestRun run, List<CommunityView> communities) {
    }

    public record MyMembership(
            String communityId, Integer clusterNo, String communityName, String color,
            String pointId, Double x, Double y, Double distanceToCenter
    ) {
    }

    public record MyCommunity(String runId, String version, MyMembership membership) {
    }

    public record CommunitySummary(
            String communityId, String runId, Integer clusterNo, String name,
            String color, Integer memberCount
    ) {
    }

    public record AdminMember(
            String userId, String name, String college, String grade,
            String pointId, Double x, Double y, Double distanceToCenter
    ) {
    }

    public record MemberPage(
            CommunitySummary community, List<AdminMember> items,
            int page, int size, long totalElements, int totalPages
    ) {
    }
}
