package com.example.campusactivity.service.clustering;

import com.example.campusactivity.dto.clustering.ClusteringRunDetailResponse;
import com.example.campusactivity.dto.clustering.CurrentUserClusteringResponse;
import com.example.campusactivity.dto.clustering.LatestClusteringResponse;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.CommunityMemberRepository;
import com.example.campusactivity.repository.CommunityRepository;
import com.example.campusactivity.repository.projection.ClusteringRunQueryProjection;
import com.example.campusactivity.repository.projection.CommunityMemberPointProjection;
import com.example.campusactivity.repository.projection.CommunityQueryProjection;
import com.example.campusactivity.repository.projection.CurrentUserMembershipProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityClusteringQueryServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-07-17T01:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-07-17T01:01:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-07-17T01:02:00Z");

    private ClusteringRunRepository runRepository;
    private CommunityRepository communityRepository;
    private CommunityMemberRepository memberRepository;
    private CommunityClusteringQueryService service;

    @BeforeEach
    void setUp() {
        runRepository = mock(ClusteringRunRepository.class);
        communityRepository = mock(CommunityRepository.class);
        memberRepository = mock(CommunityMemberRepository.class);
        service = new CommunityClusteringQueryService(
                runRepository,
                communityRepository,
                memberRepository,
                new ClusteringStoredJsonParser(new ObjectMapper())
        );
    }

    @Test
    void returnsPendingAndRunningNullResultFields() {
        ClusteringRunQueryProjection pending = run(ClusteringRunStatus.PENDING);
        when(runRepository.findQueryProjectionById("run-pending"))
                .thenReturn(Optional.of(pending));

        ClusteringRunDetailResponse pendingResponse = service.findRunById("run-pending");

        assertThat(pendingResponse.status()).isEqualTo(ClusteringRunStatus.PENDING);
        assertThat(pendingResponse.sampleCount()).isNull();
        assertThat(pendingResponse.startedAt()).isNull();
        assertThat(pendingResponse.finishedAt()).isNull();
        assertThat(pendingResponse.metrics()).isNull();
        assertThat(pendingResponse.failure()).isNull();

        ClusteringRunQueryProjection running = run(ClusteringRunStatus.RUNNING);
        when(runRepository.findQueryProjectionById("run-running"))
                .thenReturn(Optional.of(running));

        ClusteringRunDetailResponse runningResponse = service.findRunById("run-running");

        assertThat(runningResponse.status()).isEqualTo(ClusteringRunStatus.RUNNING);
        assertThat(runningResponse.startedAt()).isEqualTo(STARTED_AT);
        assertThat(runningResponse.finishedAt()).isNull();
        assertThat(runningResponse.metrics()).isNull();
        assertThat(runningResponse.failure()).isNull();
    }

    @Test
    void returnsStructuredSuccessMetricsAndFixedFailure() {
        ClusteringRunQueryProjection success = run(ClusteringRunStatus.SUCCESS);
        when(runRepository.findQueryProjectionById("run-success"))
                .thenReturn(Optional.of(success));

        ClusteringRunDetailResponse successResponse = service.findRunById("run-success");

        assertThat(successResponse.metrics().inertia()).isEqualTo(4.5);
        assertThat(successResponse.metrics().pcaExplainedVarianceRatio())
                .containsExactly(0.6, 0.3);
        assertThat(successResponse.failure()).isNull();

        ClusteringRunQueryProjection failed = run(ClusteringRunStatus.FAILED);
        when(runRepository.findQueryProjectionById("run-failed"))
                .thenReturn(Optional.of(failed));

        ClusteringRunDetailResponse failedResponse = service.findRunById("run-failed");

        assertThat(failedResponse.metrics()).isNull();
        assertThat(failedResponse.failure().code())
                .isEqualTo(ClusteringRunFailureCode.INTERNAL_ERROR);
        assertThat(failedResponse.failure().message())
                .isEqualTo(ClusteringRunFailureCode.INTERNAL_ERROR.errorMessage());
    }

    @Test
    void rejectsInvalidOrMissingRunIdWithoutEchoingIt() {
        assertQueryCode(
                () -> service.findRunById(null),
                ClusteringQueryCode.INVALID_RUN_ID,
                null
        );
        assertQueryCode(
                () -> service.findRunById(" "),
                ClusteringQueryCode.INVALID_RUN_ID,
                " "
        );
        String longId = "x".repeat(65);
        assertQueryCode(
                () -> service.findRunById(longId),
                ClusteringQueryCode.INVALID_RUN_ID,
                longId
        );

        when(runRepository.findQueryProjectionById("missing-secret-run"))
                .thenReturn(Optional.empty());
        assertQueryCode(
                () -> service.findRunById("missing-secret-run"),
                ClusteringQueryCode.RUN_NOT_FOUND,
                "missing-secret-run"
        );
    }

    @Test
    void rejectsDamagedMetricsFailureAndStateTimes() {
        ClusteringRunQueryProjection badMetrics = run(ClusteringRunStatus.SUCCESS);
        when(badMetrics.getMetricsJson()).thenReturn("{\"private\":\"value\"}");
        when(runRepository.findQueryProjectionById("bad-metrics"))
                .thenReturn(Optional.of(badMetrics));
        assertCorrupt(() -> service.findRunById("bad-metrics"));

        ClusteringRunQueryProjection badFailure = run(ClusteringRunStatus.FAILED);
        when(badFailure.getErrorMessage()).thenReturn("INTERNAL_ERROR: database details");
        when(runRepository.findQueryProjectionById("bad-failure"))
                .thenReturn(Optional.of(badFailure));
        assertCorrupt(() -> service.findRunById("bad-failure"));

        ClusteringRunQueryProjection badPending = run(ClusteringRunStatus.PENDING);
        when(badPending.getStartedAt()).thenReturn(STARTED_AT);
        when(runRepository.findQueryProjectionById("bad-pending"))
                .thenReturn(Optional.of(badPending));
        assertCorrupt(() -> service.findRunById("bad-pending"));

        ClusteringRunQueryProjection badSuccessTime = run(ClusteringRunStatus.SUCCESS);
        when(badSuccessTime.getFinishedAt()).thenReturn(CREATED_AT.minusSeconds(1));
        when(runRepository.findQueryProjectionById("bad-success-time"))
                .thenReturn(Optional.of(badSuccessTime));
        assertCorrupt(() -> service.findRunById("bad-success-time"));
    }

    @Test
    void returnsLatestWithClusterAndUnicodePointOrderingAndCurrentUserMarker() {
        ClusteringRunQueryProjection run = successfulRun();
        List<CommunityQueryProjection> communities = List.of(
                community("community-1", 1, 1, "[\"羽毛球\"]"),
                community("community-0", 0, 2, "[\"AI\",\"编程\"]")
        );
        List<CommunityMemberPointProjection> points = List.of(
                point("p-\uD800\uDC00", "community-0", 0, false),
                point("p-current", "community-1", 1, true),
                point("p-\uE000", "community-0", 0, false)
        );
        when(run.getSampleCount()).thenReturn(3);
        stubLatest(run, communities, points);

        LatestClusteringResponse response = service.findLatestClustering("private-user");

        assertThat(response.communities())
                .extracting(community -> community.clusterNo())
                .containsExactly(0, 1);
        assertThat(response.communities().get(0).points())
                .extracting(point -> point.pointId())
                .containsExactly("p-\uE000", "p-\uD800\uDC00");
        assertThat(response.communities().get(1).points())
                .singleElement()
                .satisfies(point -> assertThat(point.currentUser()).isTrue());
        assertThat(response.toString())
                .doesNotContain("private-user", "userId", "distanceToCenter");
    }

    @Test
    void returnsAllFalseWhenCurrentUserHasNoMembership() {
        ClusteringRunQueryProjection run = successfulRun();
        List<CommunityQueryProjection> communities = validCommunities();
        List<CommunityMemberPointProjection> points = validPoints(false, false);
        stubLatest(run, communities, points);

        LatestClusteringResponse response = service.findLatestClustering("absent-user");

        assertThat(response.communities())
                .flatExtracting(community -> community.points())
                .allMatch(point -> !point.currentUser());
    }

    @Test
    void rejectsMissingSuccessfulRunAndInvalidCurrentUserId() {
        when(runRepository.findLatestSuccessfulProjection()).thenReturn(Optional.empty());
        assertQueryCode(
                () -> service.findLatestClustering("user"),
                ClusteringQueryCode.NO_SUCCESSFUL_RUN,
                "user"
        );
        assertQueryCode(
                () -> service.findLatestClustering(" "),
                ClusteringQueryCode.INVALID_CURRENT_USER_ID,
                " "
        );
        String longUserId = "u".repeat(256);
        assertQueryCode(
                () -> service.findCurrentUserClustering(longUserId),
                ClusteringQueryCode.INVALID_CURRENT_USER_ID,
                longUserId
        );
    }

    @Test
    void rejectsCommunityCountAndClusterNumberCorruption() {
        ClusteringRunQueryProjection run = successfulRun();
        stubLatest(
                run,
                List.of(community("community-0", 0, 2, "[]")),
                validPoints(false, false)
        );
        assertCorrupt(() -> service.findLatestClustering("user"));

        List<CommunityQueryProjection> duplicateClusters = List.of(
                community("community-a", 0, 1, "[]"),
                community("community-b", 0, 1, "[]")
        );
        stubLatest(run, duplicateClusters, List.of(
                point("point-a", "community-a", 0, false),
                point("point-b", "community-b", 0, false)
        ));
        assertCorrupt(() -> service.findLatestClustering("user"));
    }

    @Test
    void rejectsMemberCountsAndTotalPointCountCorruption() {
        ClusteringRunQueryProjection run = successfulRun();
        List<CommunityQueryProjection> wrongMemberCount = List.of(
                community("community-0", 0, 2, "[]"),
                community("community-1", 1, 1, "[]")
        );
        stubLatest(run, wrongMemberCount, validPoints(false, false));
        assertCorrupt(() -> service.findLatestClustering("user"));

        when(run.getSampleCount()).thenReturn(3);
        stubLatest(run, validCommunities(), validPoints(false, false));
        assertCorrupt(() -> service.findLatestClustering("user"));
    }

    @Test
    void rejectsUnknownCommunityDuplicatePointAndMultipleCurrentUserHits() {
        ClusteringRunQueryProjection run = successfulRun();
        List<CommunityMemberPointProjection> unknownCommunity = List.of(
                point("point-0", "unknown", 0, false),
                point("point-1", "community-1", 1, false)
        );
        stubLatest(run, validCommunities(), unknownCommunity);
        assertCorrupt(() -> service.findLatestClustering("user"));

        List<CommunityMemberPointProjection> duplicatePoints = List.of(
                point("same-point", "community-0", 0, false),
                point("same-point", "community-1", 1, false)
        );
        stubLatest(run, validCommunities(), duplicatePoints);
        assertCorrupt(() -> service.findLatestClustering("user"));

        stubLatest(run, validCommunities(), validPoints(true, true));
        assertCorrupt(() -> service.findLatestClustering("user"));
    }

    @Test
    void rejectsCorruptTopInterestsAndMissingSuccessFields() {
        ClusteringRunQueryProjection run = successfulRun();
        List<CommunityQueryProjection> corruptInterests = List.of(
                community("community-0", 0, 1, "[\"AI\",\"AI\"]"),
                community("community-1", 1, 1, "[]")
        );
        stubLatest(run, corruptInterests, validPoints(false, false));
        assertCorrupt(() -> service.findLatestClustering("user"));

        when(run.getMetricsJson()).thenReturn(null);
        stubLatest(run, validCommunities(), validPoints(false, false));
        assertCorrupt(() -> service.findLatestClustering("user"));

        when(run.getMetricsJson())
                .thenReturn("{\"inertia\":4.5,\"pcaExplainedVarianceRatio\":[0.6,0.3]}");
        when(run.getFinishedAt()).thenReturn(null);
        assertCorrupt(() -> service.findLatestClustering("user"));

        when(run.getFinishedAt()).thenReturn(FINISHED_AT);
        when(run.getSampleCount()).thenReturn(null);
        assertCorrupt(() -> service.findLatestClustering("user"));
    }

    @Test
    void returnsCurrentUserMembershipOrNullWithoutUserId() {
        ClusteringRunQueryProjection run = successfulRun();
        CurrentUserMembershipProjection membership = membership(0.75);
        when(runRepository.findLatestSuccessfulProjection())
                .thenReturn(Optional.of(run));
        when(memberRepository.findMembershipProjection("run-success", "private-user"))
                .thenReturn(Optional.of(membership));

        CurrentUserClusteringResponse response =
                service.findCurrentUserClustering("private-user");

        assertThat(response.runId()).isEqualTo("run-success");
        assertThat(response.version()).isEqualTo("version-success");
        assertThat(response.membership().communityId()).isEqualTo("community-0");
        assertThat(response.membership().distanceToCenter()).isEqualTo(0.75);
        assertThat(response.toString()).doesNotContain("private-user", "userId");

        when(memberRepository.findMembershipProjection("run-success", "absent-user"))
                .thenReturn(Optional.empty());
        assertThat(service.findCurrentUserClustering("absent-user").membership()).isNull();
    }

    @Test
    void rejectsInvalidMembershipDistanceAndNoSuccessfulRunForMe() {
        ClusteringRunQueryProjection run = successfulRun();
        when(runRepository.findLatestSuccessfulProjection())
                .thenReturn(Optional.of(run));
        CurrentUserMembershipProjection nanDistance = membership(Double.NaN);
        when(memberRepository.findMembershipProjection("run-success", "private-user"))
                .thenReturn(Optional.of(nanDistance));
        assertCorrupt(() -> service.findCurrentUserClustering("private-user"));

        CurrentUserMembershipProjection negativeDistance = membership(-0.01);
        when(memberRepository.findMembershipProjection("run-success", "private-user"))
                .thenReturn(Optional.of(negativeDistance));
        assertCorrupt(() -> service.findCurrentUserClustering("private-user"));

        when(runRepository.findLatestSuccessfulProjection()).thenReturn(Optional.empty());
        assertQueryCode(
                () -> service.findCurrentUserClustering("private-user"),
                ClusteringQueryCode.NO_SUCCESSFUL_RUN,
                "private-user"
        );
    }

    private void stubLatest(
            ClusteringRunQueryProjection run,
            List<CommunityQueryProjection> communities,
            List<CommunityMemberPointProjection> points
    ) {
        when(runRepository.findLatestSuccessfulProjection()).thenReturn(Optional.of(run));
        when(communityRepository.findQueryProjectionsByRunId("run-success"))
                .thenReturn(communities);
        when(memberRepository.findPointProjectionsByRunId(
                "run-success",
                "private-user"
        )).thenReturn(points);
        when(memberRepository.findPointProjectionsByRunId(
                "run-success",
                "absent-user"
        )).thenReturn(points);
        when(memberRepository.findPointProjectionsByRunId("run-success", "user"))
                .thenReturn(points);
    }

    private static ClusteringRunQueryProjection successfulRun() {
        return run(ClusteringRunStatus.SUCCESS);
    }

    private static ClusteringRunQueryProjection run(ClusteringRunStatus status) {
        ClusteringRunQueryProjection run = mock(ClusteringRunQueryProjection.class);
        when(run.getId()).thenReturn(switch (status) {
            case PENDING -> "run-pending";
            case RUNNING -> "run-running";
            case SUCCESS -> "run-success";
            case FAILED -> "run-failed";
        });
        when(run.getVersion()).thenReturn("version-" + status.name().toLowerCase());
        when(run.getAlgorithm()).thenReturn(ClusteringAlgorithm.KMEANS);
        when(run.getClusterCount()).thenReturn(2);
        when(run.getRandomState()).thenReturn(42);
        when(run.getStatus()).thenReturn(status);
        when(run.getFeatureSchemaVersion()).thenReturn("community-features-v1");
        when(run.getCreatedBy()).thenReturn("admin");
        when(run.getCreatedAt()).thenReturn(CREATED_AT);
        switch (status) {
            case PENDING -> {
                when(run.getSampleCount()).thenReturn(null);
            }
            case RUNNING -> {
                when(run.getSampleCount()).thenReturn(2);
                when(run.getStartedAt()).thenReturn(STARTED_AT);
            }
            case SUCCESS -> {
                when(run.getSampleCount()).thenReturn(2);
                when(run.getStartedAt()).thenReturn(STARTED_AT);
                when(run.getFinishedAt()).thenReturn(FINISHED_AT);
                when(run.getMetricsJson()).thenReturn(
                        "{\"inertia\":4.5,\"pcaExplainedVarianceRatio\":[0.6,0.3]}"
                );
            }
            case FAILED -> {
                when(run.getSampleCount()).thenReturn(null);
                when(run.getFinishedAt()).thenReturn(FINISHED_AT);
                when(run.getErrorMessage()).thenReturn(
                        ClusteringRunFailureCode.INTERNAL_ERROR.errorMessage()
                );
            }
        }
        return run;
    }

    private static List<CommunityQueryProjection> validCommunities() {
        return List.of(
                community("community-0", 0, 1, "[\"AI\"]"),
                community("community-1", 1, 1, "[\"羽毛球\"]")
        );
    }

    private static CommunityQueryProjection community(
            String id,
            int clusterNo,
            int memberCount,
            String interestsJson
    ) {
        CommunityQueryProjection community = mock(CommunityQueryProjection.class);
        when(community.getCommunityId()).thenReturn(id);
        when(community.getClusterNo()).thenReturn(clusterNo);
        when(community.getName()).thenReturn("社区 " + (clusterNo + 1));
        when(community.getDescription()).thenReturn("描述");
        when(community.getMemberCount()).thenReturn(memberCount);
        when(community.getTopInterestsJson()).thenReturn(interestsJson);
        when(community.getColor()).thenReturn(clusterNo == 0 ? "#1677FF" : "#52C41A");
        return community;
    }

    private static List<CommunityMemberPointProjection> validPoints(
            boolean firstCurrent,
            boolean secondCurrent
    ) {
        return List.of(
                point("point-0", "community-0", 0, firstCurrent),
                point("point-1", "community-1", 1, secondCurrent)
        );
    }

    private static CommunityMemberPointProjection point(
            String pointId,
            String communityId,
            int clusterNo,
            boolean currentUser
    ) {
        CommunityMemberPointProjection point = mock(CommunityMemberPointProjection.class);
        when(point.getPointId()).thenReturn(pointId);
        when(point.getCommunityId()).thenReturn(communityId);
        when(point.getClusterNo()).thenReturn(clusterNo);
        when(point.getCoordinateX()).thenReturn(20.0 + clusterNo);
        when(point.getCoordinateY()).thenReturn(70.0 - clusterNo);
        when(point.getCurrentUser()).thenReturn(currentUser);
        return point;
    }

    private static CurrentUserMembershipProjection membership(double distance) {
        CurrentUserMembershipProjection membership =
                mock(CurrentUserMembershipProjection.class);
        when(membership.getPointId()).thenReturn("point-0");
        when(membership.getCommunityId()).thenReturn("community-0");
        when(membership.getClusterNo()).thenReturn(0);
        when(membership.getCommunityName()).thenReturn("社区 1");
        when(membership.getColor()).thenReturn("#1677FF");
        when(membership.getCoordinateX()).thenReturn(20.0);
        when(membership.getCoordinateY()).thenReturn(70.0);
        when(membership.getDistanceToCenter()).thenReturn(distance);
        return membership;
    }

    private static void assertCorrupt(ThrowingOperation operation) {
        assertQueryCode(operation, ClusteringQueryCode.CORRUPT_STORED_DATA, null);
    }

    private static void assertQueryCode(
            ThrowingOperation operation,
            ClusteringQueryCode code,
            String secret
    ) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ClusteringQueryException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(code);
                    assertThat(exception.getMessage()).isEqualTo(code.safeMessage());
                    assertThat(exception.getCause()).isNull();
                    if (secret != null && !secret.isBlank()) {
                        assertThat(exception.getMessage()).doesNotContain(secret);
                        assertThat(exception.toString()).doesNotContain(secret);
                    }
                });
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }
}
