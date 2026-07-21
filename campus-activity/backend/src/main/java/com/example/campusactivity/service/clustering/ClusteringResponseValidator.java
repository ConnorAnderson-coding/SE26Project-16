package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.ClusteringMetrics;
import com.example.campusactivity.client.clustering.dto.ClusteringRequest;
import com.example.campusactivity.client.clustering.dto.ClusteringResponse;
import com.example.campusactivity.client.clustering.dto.CommunitySummary;
import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.client.clustering.dto.MemberResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Component
public final class ClusteringResponseValidator {
    private static final int PCA_COMPONENT_COUNT = 2;
    private static final int MAX_TOP_INTERESTS = 3;
    private static final Comparator<MemberResult> MEMBER_ORDER = Comparator.comparing(
            MemberResult::userId,
            UnicodeCodePointComparator.INSTANCE
    );

    public ValidatedClusteringResult validate(
            ClusteringRequest request,
            ClusteringResponse response
    ) {
        RequestSnapshot requestSnapshot = validateRequest(request);
        if (response == null) {
            fail(ClusteringResponseValidationCode.INVALID_RESPONSE);
        }

        validateMetadata(request, response, requestSnapshot.samples().size());
        ClusteringMetrics metrics = copyAndValidateMetrics(response.metrics());
        Map<Integer, CommunitySummary> communitiesByCluster = validateCommunities(
                response.communities(),
                requestSnapshot.clusterCount()
        );
        List<MemberResult> members = validateMembers(
                response.members(),
                response.sampleCount(),
                requestSnapshot.userIds(),
                communitiesByCluster.keySet()
        );
        Map<Integer, List<MemberResult>> membersByCluster = groupMembers(
                members,
                requestSnapshot.clusterCount()
        );

        reconcileMemberCounts(communitiesByCluster, membersByCluster, response.sampleCount());
        validateTopInterests(
                communitiesByCluster,
                membersByCluster,
                requestSnapshot.samplesByUserId()
        );

        List<CommunitySummary> normalizedCommunities = communitiesByCluster.values().stream()
                .map(ClusteringResponseValidator::copyCommunity)
                .toList();
        List<MemberResult> normalizedMembers = members.stream()
                .sorted(MEMBER_ORDER)
                .map(ClusteringResponseValidator::copyMember)
                .toList();
        Map<Integer, List<MemberResult>> normalizedGroups = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<MemberResult>> entry : membersByCluster.entrySet()) {
            normalizedGroups.put(
                    entry.getKey(),
                    entry.getValue().stream()
                            .sorted(MEMBER_ORDER)
                            .map(ClusteringResponseValidator::copyMember)
                            .toList()
            );
        }

        return new ValidatedClusteringResult(
                response.runId(),
                response.version(),
                response.algorithm(),
                response.clusterCount(),
                response.sampleCount(),
                metrics,
                normalizedCommunities,
                normalizedMembers,
                normalizedGroups
        );
    }

    private static RequestSnapshot validateRequest(ClusteringRequest request) {
        if (request == null || request.samples() == null || request.clusterCount() == null) {
            fail(ClusteringResponseValidationCode.INVALID_REQUEST);
        }
        List<FeatureSample> samples = request.samples();
        int clusterCount = request.clusterCount();
        if (samples.size() < 2 || clusterCount < 2 || clusterCount > samples.size()) {
            fail(ClusteringResponseValidationCode.INVALID_REQUEST);
        }

        Map<String, FeatureSample> samplesByUserId = new HashMap<>();
        for (FeatureSample sample : samples) {
            if (sample == null || sample.userId() == null || sample.userId().isBlank()) {
                fail(ClusteringResponseValidationCode.INVALID_REQUEST);
            }
            if (samplesByUserId.putIfAbsent(sample.userId(), sample) != null) {
                fail(ClusteringResponseValidationCode.INPUT_USER_DUPLICATE);
            }
        }
        if (samplesByUserId.size() != samples.size()) {
            fail(ClusteringResponseValidationCode.INPUT_USER_DUPLICATE);
        }
        return new RequestSnapshot(
                clusterCount,
                List.copyOf(samples),
                Collections.unmodifiableMap(new HashMap<>(samplesByUserId)),
                Collections.unmodifiableSet(new HashSet<>(samplesByUserId.keySet()))
        );
    }

    private static void validateMetadata(
            ClusteringRequest request,
            ClusteringResponse response,
            int inputSampleCount
    ) {
        if (!Objects.equals(response.runId(), request.runId())) {
            fail(ClusteringResponseValidationCode.RUN_ID_MISMATCH);
        }
        if (!Objects.equals(response.version(), request.version())) {
            fail(ClusteringResponseValidationCode.VERSION_MISMATCH);
        }
        if (!Objects.equals(response.algorithm(), request.algorithm())) {
            fail(ClusteringResponseValidationCode.ALGORITHM_MISMATCH);
        }
        if (!Objects.equals(response.clusterCount(), request.clusterCount())) {
            fail(ClusteringResponseValidationCode.CLUSTER_COUNT_MISMATCH);
        }
        if (response.sampleCount() == null || response.sampleCount() != inputSampleCount) {
            fail(ClusteringResponseValidationCode.SAMPLE_COUNT_MISMATCH);
        }
    }

    private static ClusteringMetrics copyAndValidateMetrics(ClusteringMetrics metrics) {
        if (metrics == null || metrics.inertia() == null || !Double.isFinite(metrics.inertia())
                || metrics.inertia() < 0.0) {
            fail(ClusteringResponseValidationCode.INVALID_METRICS);
        }
        List<Double> ratios = metrics.pcaExplainedVarianceRatio();
        if (ratios == null || ratios.size() != PCA_COMPONENT_COUNT) {
            fail(ClusteringResponseValidationCode.INVALID_METRICS);
        }
        for (Double ratio : ratios) {
            if (ratio == null || !Double.isFinite(ratio) || ratio < 0.0 || ratio > 1.0) {
                fail(ClusteringResponseValidationCode.INVALID_METRICS);
            }
        }
        return new ClusteringMetrics(metrics.inertia(), ratios);
    }

    private static Map<Integer, CommunitySummary> validateCommunities(
            List<CommunitySummary> communities,
            int clusterCount
    ) {
        if (communities == null || communities.size() != clusterCount) {
            fail(ClusteringResponseValidationCode.COMMUNITY_COUNT_MISMATCH);
        }
        Map<Integer, CommunitySummary> byCluster = new TreeMap<>();
        for (CommunitySummary community : communities) {
            if (community == null) {
                fail(ClusteringResponseValidationCode.INVALID_COMMUNITY_VALUE);
            }
            Integer clusterNo = community.clusterNo();
            if (clusterNo == null || clusterNo < 0 || clusterNo >= clusterCount) {
                fail(ClusteringResponseValidationCode.INVALID_CLUSTER_NUMBER);
            }
            if (byCluster.putIfAbsent(clusterNo, community) != null) {
                fail(ClusteringResponseValidationCode.DUPLICATE_CLUSTER_NUMBER);
            }
            if (community.memberCount() == null || community.memberCount() <= 0) {
                fail(ClusteringResponseValidationCode.INVALID_COMMUNITY_VALUE);
            }
            validateTopInterestValues(community.topInterests());
        }
        if (!byCluster.keySet().equals(expectedClusterNumbers(clusterCount))) {
            fail(ClusteringResponseValidationCode.INCOMPLETE_CLUSTER_NUMBER_SET);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(byCluster));
    }

    private static void validateTopInterestValues(List<String> topInterests) {
        if (topInterests == null || topInterests.size() > MAX_TOP_INTERESTS) {
            fail(ClusteringResponseValidationCode.INVALID_TOP_INTEREST);
        }
        Set<String> unique = new HashSet<>();
        for (String interest : topInterests) {
            if (interest == null || interest.isBlank()) {
                fail(ClusteringResponseValidationCode.INVALID_TOP_INTEREST);
            }
            if (!unique.add(interest)) {
                fail(ClusteringResponseValidationCode.DUPLICATE_TOP_INTEREST);
            }
        }
    }

    private static List<MemberResult> validateMembers(
            List<MemberResult> members,
            Integer sampleCount,
            Set<String> inputUserIds,
            Set<Integer> knownClusters
    ) {
        if (members == null) {
            fail(ClusteringResponseValidationCode.INVALID_RESPONSE);
        }
        Map<String, MemberResult> membersByUserId = new HashMap<>();
        String previousUserId = null;
        boolean invalidOrder = false;
        for (MemberResult member : members) {
            if (member == null || member.userId() == null || member.userId().isBlank()) {
                fail(ClusteringResponseValidationCode.INVALID_MEMBER_VALUE);
            }
            String userId = member.userId();
            if (membersByUserId.putIfAbsent(userId, member) != null) {
                fail(ClusteringResponseValidationCode.RESPONSE_USER_DUPLICATE);
            }
            if (previousUserId != null
                    && UnicodeCodePointComparator.INSTANCE.compare(previousUserId, userId) > 0) {
                invalidOrder = true;
            }
            previousUserId = userId;

            if (member.clusterNo() == null || !knownClusters.contains(member.clusterNo())) {
                fail(ClusteringResponseValidationCode.MEMBER_CLUSTER_UNKNOWN);
            }
            validateMemberValues(member);
        }

        Set<String> responseUserIds = membersByUserId.keySet();
        if (!responseUserIds.containsAll(inputUserIds)) {
            fail(ClusteringResponseValidationCode.RESPONSE_USER_MISSING);
        }
        if (!inputUserIds.containsAll(responseUserIds)) {
            fail(ClusteringResponseValidationCode.RESPONSE_USER_UNEXPECTED);
        }
        if (sampleCount == null || members.size() != sampleCount) {
            fail(ClusteringResponseValidationCode.SAMPLE_COUNT_MISMATCH);
        }
        if (invalidOrder) {
            fail(ClusteringResponseValidationCode.INVALID_MEMBER_ORDER);
        }
        return List.copyOf(members);
    }

    private static void validateMemberValues(MemberResult member) {
        if (!isCoordinate(member.coordinateX()) || !isCoordinate(member.coordinateY())) {
            fail(ClusteringResponseValidationCode.INVALID_MEMBER_VALUE);
        }
        Double distance = member.distanceToCenter();
        if (distance == null || !Double.isFinite(distance) || distance < 0.0) {
            fail(ClusteringResponseValidationCode.INVALID_MEMBER_VALUE);
        }
    }

    private static boolean isCoordinate(Double coordinate) {
        return coordinate != null && Double.isFinite(coordinate)
                && coordinate >= 0.0 && coordinate <= 100.0;
    }

    private static Map<Integer, List<MemberResult>> groupMembers(
            List<MemberResult> members,
            int clusterCount
    ) {
        Map<Integer, List<MemberResult>> groups = new TreeMap<>();
        for (int clusterNo = 0; clusterNo < clusterCount; clusterNo++) {
            groups.put(clusterNo, new ArrayList<>());
        }
        for (MemberResult member : members) {
            groups.get(member.clusterNo()).add(member);
        }
        Map<Integer, List<MemberResult>> immutableGroups = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<MemberResult>> entry : groups.entrySet()) {
            List<MemberResult> sorted = entry.getValue().stream().sorted(MEMBER_ORDER).toList();
            immutableGroups.put(entry.getKey(), sorted);
        }
        return Collections.unmodifiableMap(immutableGroups);
    }

    private static void reconcileMemberCounts(
            Map<Integer, CommunitySummary> communities,
            Map<Integer, List<MemberResult>> membersByCluster,
            int sampleCount
    ) {
        long summaryTotal = communities.values().stream()
                .mapToLong(CommunitySummary::memberCount)
                .sum();
        if (summaryTotal != sampleCount) {
            fail(ClusteringResponseValidationCode.TOTAL_MEMBER_COUNT_MISMATCH);
        }

        long actualTotal = 0L;
        for (Map.Entry<Integer, CommunitySummary> entry : communities.entrySet()) {
            int actualCount = membersByCluster.get(entry.getKey()).size();
            actualTotal += actualCount;
            if (actualCount == 0 || entry.getValue().memberCount() != actualCount) {
                fail(ClusteringResponseValidationCode.COMMUNITY_MEMBER_COUNT_MISMATCH);
            }
        }
        if (actualTotal != sampleCount) {
            fail(ClusteringResponseValidationCode.TOTAL_MEMBER_COUNT_MISMATCH);
        }
    }

    private static void validateTopInterests(
            Map<Integer, CommunitySummary> communities,
            Map<Integer, List<MemberResult>> membersByCluster,
            Map<String, FeatureSample> samplesByUserId
    ) {
        for (Map.Entry<Integer, CommunitySummary> entry : communities.entrySet()) {
            Map<String, Integer> interestCounts = new HashMap<>();
            for (MemberResult member : membersByCluster.get(entry.getKey())) {
                FeatureSample sample = samplesByUserId.get(member.userId());
                if (sample == null || sample.interests() == null) {
                    fail(ClusteringResponseValidationCode.INVALID_REQUEST);
                }
                for (String interest : new HashSet<>(sample.interests())) {
                    interestCounts.merge(interest, 1, Integer::sum);
                }
            }

            List<String> actual = entry.getValue().topInterests();
            for (String interest : actual) {
                if (!interestCounts.containsKey(interest)) {
                    fail(ClusteringResponseValidationCode.TOP_INTEREST_NOT_IN_CLUSTER);
                }
            }
            List<String> expected = interestCounts.entrySet().stream()
                    .sorted((left, right) -> {
                        int frequencyOrder = Integer.compare(right.getValue(), left.getValue());
                        return frequencyOrder != 0
                                ? frequencyOrder
                                : UnicodeCodePointComparator.INSTANCE.compare(
                                        left.getKey(),
                                        right.getKey()
                                );
                    })
                    .limit(MAX_TOP_INTERESTS)
                    .map(Map.Entry::getKey)
                    .toList();
            if (!actual.equals(expected)) {
                fail(ClusteringResponseValidationCode.INVALID_TOP_INTEREST_ORDER);
            }
        }
    }

    private static Set<Integer> expectedClusterNumbers(int clusterCount) {
        Set<Integer> expected = new TreeSet<>();
        for (int clusterNo = 0; clusterNo < clusterCount; clusterNo++) {
            expected.add(clusterNo);
        }
        return expected;
    }

    private static CommunitySummary copyCommunity(CommunitySummary community) {
        return new CommunitySummary(
                community.clusterNo(),
                community.memberCount(),
                community.topInterests()
        );
    }

    private static MemberResult copyMember(MemberResult member) {
        return new MemberResult(
                member.userId(),
                member.clusterNo(),
                member.coordinateX(),
                member.coordinateY(),
                member.distanceToCenter()
        );
    }

    private static void fail(ClusteringResponseValidationCode code) {
        throw new ClusteringResponseValidationException(code);
    }

    private record RequestSnapshot(
            int clusterCount,
            List<FeatureSample> samples,
            Map<String, FeatureSample> samplesByUserId,
            Set<String> userIds
    ) {
    }
}
