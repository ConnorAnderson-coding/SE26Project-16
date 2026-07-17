package com.example.campusactivity.service.clustering;

import com.example.campusactivity.dto.clustering.ClusteringFailureResponse;
import com.example.campusactivity.dto.clustering.ClusteringMetricsResponse;
import com.example.campusactivity.dto.clustering.ClusteringRunDetailResponse;
import com.example.campusactivity.dto.clustering.ClusteringRunSummaryResponse;
import com.example.campusactivity.dto.clustering.CommunityMemberPointResponse;
import com.example.campusactivity.dto.clustering.CommunityResponse;
import com.example.campusactivity.dto.clustering.CurrentUserClusteringResponse;
import com.example.campusactivity.dto.clustering.CurrentUserMembershipResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class CommunityClusteringQueryService {
    private static final int RUN_ID_MAX_LENGTH = 64;
    private static final int VERSION_MAX_LENGTH = 64;
    private static final int FEATURE_SCHEMA_VERSION_MAX_LENGTH = 64;
    private static final int CREATED_BY_MAX_LENGTH = 255;
    private static final int CURRENT_USER_ID_MAX_LENGTH = 255;
    private static final int COMMUNITY_ID_MAX_LENGTH = 64;
    private static final int POINT_ID_MAX_LENGTH = 64;
    private static final int COMMUNITY_NAME_MAX_LENGTH = 100;
    private static final int COMMUNITY_DESCRIPTION_MAX_LENGTH = 500;
    private static final int COMMUNITY_COLOR_MAX_LENGTH = 16;

    private final ClusteringRunRepository clusteringRunRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final ClusteringStoredJsonParser storedJsonParser;

    public CommunityClusteringQueryService(
            ClusteringRunRepository clusteringRunRepository,
            CommunityRepository communityRepository,
            CommunityMemberRepository communityMemberRepository,
            ClusteringStoredJsonParser storedJsonParser
    ) {
        this.clusteringRunRepository = clusteringRunRepository;
        this.communityRepository = communityRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.storedJsonParser = storedJsonParser;
    }

    public ClusteringRunDetailResponse findRunById(String runId) {
        if (!validRequiredString(runId, RUN_ID_MAX_LENGTH)) {
            throw queryError(ClusteringQueryCode.INVALID_RUN_ID);
        }

        ClusteringRunQueryProjection run = clusteringRunRepository
                .findQueryProjectionById(runId)
                .orElseThrow(() -> queryError(ClusteringQueryCode.RUN_NOT_FOUND));
        return toRunDetail(run);
    }

    public LatestClusteringResponse findLatestClustering(String currentUserId) {
        validateCurrentUserId(currentUserId);
        ClusteringRunQueryProjection run = latestSuccessfulRun();
        ClusteringMetricsResponse ignoredMetrics = validateSuccessfulRun(run);
        if (ignoredMetrics == null) {
            throw corrupt();
        }

        List<CommunityQueryProjection> storedCommunities =
                communityRepository.findQueryProjectionsByRunId(run.getId());
        List<CommunityMemberPointProjection> storedPoints =
                communityMemberRepository.findPointProjectionsByRunId(
                        run.getId(),
                        currentUserId
                );
        return assembleLatest(run, storedCommunities, storedPoints);
    }

    public CurrentUserClusteringResponse findCurrentUserClustering(String currentUserId) {
        validateCurrentUserId(currentUserId);
        ClusteringRunQueryProjection run = latestSuccessfulRun();
        validateSuccessfulRun(run);

        CurrentUserMembershipResponse membership = communityMemberRepository
                .findMembershipProjection(run.getId(), currentUserId)
                .map(projection -> toCurrentUserMembership(projection, run.getClusterCount()))
                .orElse(null);
        return new CurrentUserClusteringResponse(run.getId(), run.getVersion(), membership);
    }

    private ClusteringRunDetailResponse toRunDetail(ClusteringRunQueryProjection run) {
        validateCommonRun(run);

        ClusteringMetricsResponse metrics = null;
        ClusteringFailureResponse failure = null;
        switch (run.getStatus()) {
            case PENDING -> validatePendingRun(run);
            case RUNNING -> validateRunningRun(run);
            case SUCCESS -> metrics = validateSuccessfulRun(run);
            case FAILED -> failure = validateFailedRun(run);
            default -> throw corrupt();
        }

        return new ClusteringRunDetailResponse(
                run.getId(),
                run.getVersion(),
                run.getAlgorithm(),
                run.getClusterCount(),
                run.getRandomState(),
                run.getStatus(),
                run.getSampleCount(),
                run.getFeatureSchemaVersion(),
                metrics,
                failure,
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedBy()
        );
    }

    private LatestClusteringResponse assembleLatest(
            ClusteringRunQueryProjection run,
            List<CommunityQueryProjection> storedCommunities,
            List<CommunityMemberPointProjection> storedPoints
    ) {
        if (storedCommunities == null || storedPoints == null) {
            throw corrupt();
        }
        int clusterCount = run.getClusterCount();
        int sampleCount = run.getSampleCount();
        if (storedCommunities.size() != clusterCount) {
            throw corrupt();
        }

        List<CommunityQueryProjection> orderedCommunities =
                new ArrayList<>(storedCommunities);
        Map<String, CommunityAccumulator> byCommunityId = new HashMap<>();
        Set<Integer> clusterNumbers = new HashSet<>();
        for (CommunityQueryProjection community : orderedCommunities) {
            validateCommunity(community, clusterCount);
            if (!clusterNumbers.add(community.getClusterNo())
                    || byCommunityId.put(
                            community.getCommunityId(),
                            new CommunityAccumulator(
                                    community,
                                    storedJsonParser.parseTopInterests(
                                            community.getTopInterestsJson()
                                    )
                            )
                    ) != null) {
                throw corrupt();
            }
        }
        for (int clusterNo = 0; clusterNo < clusterCount; clusterNo++) {
            if (!clusterNumbers.contains(clusterNo)) {
                throw corrupt();
            }
        }
        orderedCommunities.sort(Comparator.comparingInt(CommunityQueryProjection::getClusterNo));

        Set<String> pointIds = new HashSet<>();
        int currentUserHits = 0;
        for (CommunityMemberPointProjection point : storedPoints) {
            validatePoint(point, clusterCount);
            if (!pointIds.add(point.getPointId())) {
                throw corrupt();
            }
            CommunityAccumulator community = byCommunityId.get(point.getCommunityId());
            if (community == null
                    || !community.projection().getClusterNo().equals(point.getClusterNo())) {
                throw corrupt();
            }
            if (point.getCurrentUser()) {
                currentUserHits++;
                if (currentUserHits > 1) {
                    throw corrupt();
                }
            }
            community.points().add(new CommunityMemberPointResponse(
                    point.getPointId(),
                    point.getCoordinateX(),
                    point.getCoordinateY(),
                    point.getCurrentUser()
            ));
        }
        if (storedPoints.size() != sampleCount) {
            throw corrupt();
        }

        List<CommunityResponse> responses = new ArrayList<>(clusterCount);
        for (CommunityQueryProjection storedCommunity : orderedCommunities) {
            CommunityAccumulator community = byCommunityId.get(
                    storedCommunity.getCommunityId()
            );
            if (community.points().size() != storedCommunity.getMemberCount()) {
                throw corrupt();
            }
            community.points().sort((left, right) ->
                    UnicodeCodePointComparator.INSTANCE.compare(
                            left.pointId(),
                            right.pointId()
                    )
            );
            responses.add(new CommunityResponse(
                    storedCommunity.getCommunityId(),
                    storedCommunity.getClusterNo(),
                    storedCommunity.getName(),
                    storedCommunity.getDescription(),
                    storedCommunity.getMemberCount(),
                    community.topInterests(),
                    storedCommunity.getColor(),
                    community.points()
            ));
        }

        ClusteringRunSummaryResponse summary = new ClusteringRunSummaryResponse(
                run.getId(),
                run.getVersion(),
                run.getAlgorithm(),
                clusterCount,
                sampleCount,
                run.getFinishedAt()
        );
        return new LatestClusteringResponse(summary, responses);
    }

    private CurrentUserMembershipResponse toCurrentUserMembership(
            CurrentUserMembershipProjection membership,
            Integer clusterCount
    ) {
        if (membership == null
                || !validRequiredString(membership.getPointId(), POINT_ID_MAX_LENGTH)
                || !validRequiredString(
                        membership.getCommunityId(),
                        COMMUNITY_ID_MAX_LENGTH
                )
                || membership.getClusterNo() == null
                || membership.getClusterNo() < 0
                || membership.getClusterNo() >= clusterCount
                || !validRequiredString(
                        membership.getCommunityName(),
                        COMMUNITY_NAME_MAX_LENGTH
                )
                || !validRequiredString(
                        membership.getColor(),
                        COMMUNITY_COLOR_MAX_LENGTH
                )
                || !validCoordinate(membership.getCoordinateX())
                || !validCoordinate(membership.getCoordinateY())
                || membership.getDistanceToCenter() == null
                || !Double.isFinite(membership.getDistanceToCenter())
                || membership.getDistanceToCenter() < 0.0) {
            throw corrupt();
        }
        return new CurrentUserMembershipResponse(
                membership.getCommunityId(),
                membership.getClusterNo(),
                membership.getCommunityName(),
                membership.getColor(),
                membership.getPointId(),
                membership.getCoordinateX(),
                membership.getCoordinateY(),
                membership.getDistanceToCenter()
        );
    }

    private ClusteringRunQueryProjection latestSuccessfulRun() {
        ClusteringRunQueryProjection run = clusteringRunRepository
                .findLatestSuccessfulProjection()
                .orElseThrow(() -> queryError(ClusteringQueryCode.NO_SUCCESSFUL_RUN));
        validateCommonRun(run);
        if (run.getStatus() != ClusteringRunStatus.SUCCESS) {
            throw corrupt();
        }
        return run;
    }

    private void validateCommonRun(ClusteringRunQueryProjection run) {
        if (run == null
                || !validRequiredString(run.getId(), RUN_ID_MAX_LENGTH)
                || !validRequiredString(run.getVersion(), VERSION_MAX_LENGTH)
                || run.getAlgorithm() != ClusteringAlgorithm.KMEANS
                || run.getClusterCount() == null
                || run.getClusterCount() < 2
                || run.getRandomState() == null
                || run.getRandomState() != 42
                || run.getStatus() == null
                || !validRequiredString(
                        run.getFeatureSchemaVersion(),
                        FEATURE_SCHEMA_VERSION_MAX_LENGTH
                )
                || !validRequiredString(run.getCreatedBy(), CREATED_BY_MAX_LENGTH)
                || run.getCreatedAt() == null
                || run.getSampleCount() != null && run.getSampleCount() < 0) {
            throw corrupt();
        }
    }

    private void validatePendingRun(ClusteringRunQueryProjection run) {
        if (run.getStartedAt() != null
                || run.getFinishedAt() != null
                || run.getMetricsJson() != null
                || run.getErrorMessage() != null) {
            throw corrupt();
        }
    }

    private void validateRunningRun(ClusteringRunQueryProjection run) {
        if (run.getStartedAt() == null
                || run.getFinishedAt() != null
                || run.getMetricsJson() != null
                || run.getErrorMessage() != null
                || run.getStartedAt().isBefore(run.getCreatedAt())) {
            throw corrupt();
        }
    }

    private ClusteringMetricsResponse validateSuccessfulRun(
            ClusteringRunQueryProjection run
    ) {
        validateCommonRun(run);
        if (run.getStatus() != ClusteringRunStatus.SUCCESS
                || run.getStartedAt() == null
                || run.getFinishedAt() == null
                || run.getSampleCount() == null
                || run.getSampleCount() < run.getClusterCount()
                || run.getMetricsJson() == null
                || run.getErrorMessage() != null
                || !validTerminalTimeOrder(run)) {
            throw corrupt();
        }
        return storedJsonParser.parseMetrics(run.getMetricsJson());
    }

    private ClusteringFailureResponse validateFailedRun(
            ClusteringRunQueryProjection run
    ) {
        if (run.getFinishedAt() == null
                || run.getMetricsJson() != null
                || run.getErrorMessage() == null
                || !validTerminalTimeOrder(run)) {
            throw corrupt();
        }
        return storedJsonParser.parseFailure(run.getErrorMessage());
    }

    private boolean validTerminalTimeOrder(ClusteringRunQueryProjection run) {
        Instant createdAt = run.getCreatedAt();
        Instant startedAt = run.getStartedAt();
        Instant finishedAt = run.getFinishedAt();
        if (finishedAt.isBefore(createdAt)) {
            return false;
        }
        return startedAt == null
                || !startedAt.isBefore(createdAt) && !finishedAt.isBefore(startedAt);
    }

    private void validateCommunity(
            CommunityQueryProjection community,
            int clusterCount
    ) {
        if (community == null
                || !validRequiredString(
                        community.getCommunityId(),
                        COMMUNITY_ID_MAX_LENGTH
                )
                || community.getClusterNo() == null
                || community.getClusterNo() < 0
                || community.getClusterNo() >= clusterCount
                || !validRequiredString(
                        community.getName(),
                        COMMUNITY_NAME_MAX_LENGTH
                )
                || community.getDescription() != null
                && community.getDescription().length()
                > COMMUNITY_DESCRIPTION_MAX_LENGTH
                || community.getMemberCount() == null
                || community.getMemberCount() <= 0
                || !validRequiredString(
                        community.getColor(),
                        COMMUNITY_COLOR_MAX_LENGTH
                )
                || community.getTopInterestsJson() == null) {
            throw corrupt();
        }
    }

    private void validatePoint(
            CommunityMemberPointProjection point,
            int clusterCount
    ) {
        if (point == null
                || !validRequiredString(point.getPointId(), POINT_ID_MAX_LENGTH)
                || !validRequiredString(
                        point.getCommunityId(),
                        COMMUNITY_ID_MAX_LENGTH
                )
                || point.getClusterNo() == null
                || point.getClusterNo() < 0
                || point.getClusterNo() >= clusterCount
                || !validCoordinate(point.getCoordinateX())
                || !validCoordinate(point.getCoordinateY())
                || point.getCurrentUser() == null) {
            throw corrupt();
        }
    }

    private void validateCurrentUserId(String currentUserId) {
        if (!validRequiredString(currentUserId, CURRENT_USER_ID_MAX_LENGTH)) {
            throw queryError(ClusteringQueryCode.INVALID_CURRENT_USER_ID);
        }
    }

    private boolean validCoordinate(Double coordinate) {
        return coordinate != null
                && Double.isFinite(coordinate)
                && coordinate >= 0.0
                && coordinate <= 100.0;
    }

    private boolean validRequiredString(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private ClusteringQueryException corrupt() {
        return queryError(ClusteringQueryCode.CORRUPT_STORED_DATA);
    }

    private ClusteringQueryException queryError(ClusteringQueryCode code) {
        return new ClusteringQueryException(code);
    }

    private record CommunityAccumulator(
            CommunityQueryProjection projection,
            List<String> topInterests,
            List<CommunityMemberPointResponse> points
    ) {
        private CommunityAccumulator(
                CommunityQueryProjection projection,
                List<String> topInterests
        ) {
            this(projection, topInterests, new ArrayList<>());
        }
    }
}
