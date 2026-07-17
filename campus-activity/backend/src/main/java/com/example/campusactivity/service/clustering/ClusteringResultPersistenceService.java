package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.CommunitySummary;
import com.example.campusactivity.client.clustering.dto.MemberResult;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.entity.Community;
import com.example.campusactivity.entity.CommunityMember;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.CommunityMemberRepository;
import com.example.campusactivity.repository.CommunityRepository;
import com.example.campusactivity.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Service
public class ClusteringResultPersistenceService {
    private static final List<String> DISPLAY_COLORS = List.of(
            "#1677FF",
            "#52C41A",
            "#FA8C16",
            "#EB2F96",
            "#722ED1",
            "#13C2C2",
            "#F5222D",
            "#2F54EB"
    );
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final ClusteringRunRepository runRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ClusteringIdentifierGenerator identifierGenerator;
    private final ClusteringPersistenceJsonSerializer jsonSerializer;
    private final Clock clock;
    private final EntityManager entityManager;

    public ClusteringResultPersistenceService(
            ClusteringRunRepository runRepository,
            CommunityRepository communityRepository,
            CommunityMemberRepository memberRepository,
            UserRepository userRepository,
            ClusteringIdentifierGenerator identifierGenerator,
            ClusteringPersistenceJsonSerializer jsonSerializer,
            Clock clock,
            EntityManager entityManager
    ) {
        this.runRepository = runRepository;
        this.communityRepository = communityRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.identifierGenerator = identifierGenerator;
        this.jsonSerializer = jsonSerializer;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional
    public ClusteringRunSnapshot persistSuccess(
            String runId,
            ValidatedClusteringResult result
    ) {
        try {
            if (runId == null || runId.isBlank()) {
                fail(ClusteringRunStateCode.RUN_NOT_FOUND);
            }
            ClusteringRun run = runRepository.findByIdForUpdate(runId)
                    .orElseThrow(() -> stateException(ClusteringRunStateCode.RUN_NOT_FOUND));
            requireRunning(run.getStatus());
            validateRunMetadata(run, result);

            if (communityRepository.existsByRunId(runId)
                    || memberRepository.existsByRunId(runId)) {
                fail(ClusteringRunStateCode.RUN_RESULTS_ALREADY_EXIST);
            }

            NormalizedResult normalized = normalizeResult(result);
            Map<String, UserAccount> usersById = loadUsers(normalized.userIds());
            Instant persistedAt = clock.instant();
            validatePersistenceTime(run, persistedAt);
            String metricsJson = jsonSerializer.metricsJson(result.metrics());

            Map<Integer, Community> communitiesByCluster = buildCommunities(
                    run,
                    normalized
            );
            List<CommunityMember> members = buildMembers(
                    run,
                    normalized.members(),
                    communitiesByCluster,
                    usersById,
                    persistedAt
            );

            persistCommunities(communitiesByCluster.values());
            persistMembers(members);
            verifyStoredCounts(runId, result, communitiesByCluster);

            run.setMetricsJson(metricsJson);
            run.setSampleCount(result.sampleCount());
            run.setFinishedAt(persistedAt);
            run.setErrorMessage(null);
            run.setActiveSlot(null);
            run.setStatus(ClusteringRunStatus.SUCCESS);
            runRepository.flush();
            return ClusteringRunSnapshot.from(run);
        } catch (ClusteringRunStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            fail(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
            return null;
        }
    }

    private void persistCommunities(Iterable<Community> communities) {
        for (Community community : communities) {
            entityManager.persist(community);
        }
        entityManager.flush();
    }

    private void persistMembers(Iterable<CommunityMember> members) {
        for (CommunityMember member : members) {
            entityManager.persist(member);
        }
        entityManager.flush();
    }

    private static void validateRunMetadata(
            ClusteringRun run,
            ValidatedClusteringResult result
    ) {
        if (result == null
                || !run.getId().equals(result.runId())
                || !run.getVersion().equals(result.version())
                || !run.getAlgorithm().name().equals(result.algorithm())
                || run.getClusterCount() == null
                || run.getClusterCount() != result.clusterCount()
                || run.getSampleCount() == null
                || run.getSampleCount() != result.sampleCount()
                || result.metrics() == null) {
            fail(ClusteringRunStateCode.RUN_RESULT_MISMATCH);
        }
    }

    private static NormalizedResult normalizeResult(ValidatedClusteringResult result) {
        if (result.communities() == null
                || result.communities().size() != result.clusterCount()
                || result.members() == null
                || result.members().size() != result.sampleCount()) {
            fail(ClusteringRunStateCode.RUN_RESULT_MISMATCH);
        }

        Map<Integer, CommunitySummary> summaries = new TreeMap<>();
        for (CommunitySummary summary : result.communities()) {
            if (summary == null
                    || summary.clusterNo() == null
                    || summary.clusterNo() < 0
                    || summary.clusterNo() >= result.clusterCount()
                    || summary.memberCount() == null
                    || summary.memberCount() <= 0
                    || summary.topInterests() == null
                    || summary.topInterests().size() > 3
                    || summaries.putIfAbsent(summary.clusterNo(), summary) != null) {
                fail(ClusteringRunStateCode.RUN_RESULT_MISMATCH);
            }
        }
        for (int clusterNo = 0; clusterNo < result.clusterCount(); clusterNo++) {
            if (!summaries.containsKey(clusterNo)) {
                fail(ClusteringRunStateCode.RUN_RESULT_MISMATCH);
            }
        }

        Map<Integer, Integer> actualCounts = new HashMap<>();
        Set<String> uniqueUserIds = new HashSet<>();
        List<MemberResult> sortedMembers = new ArrayList<>(result.members());
        for (MemberResult member : sortedMembers) {
            if (member == null
                    || member.userId() == null
                    || member.userId().isBlank()
                    || !uniqueUserIds.add(member.userId())
                    || member.clusterNo() == null
                    || !summaries.containsKey(member.clusterNo())) {
                fail(ClusteringRunStateCode.RUN_RESULT_MISMATCH);
            }
            actualCounts.merge(member.clusterNo(), 1, Integer::sum);
        }
        for (Map.Entry<Integer, CommunitySummary> entry : summaries.entrySet()) {
            int actualCount = actualCounts.getOrDefault(entry.getKey(), 0);
            if (actualCount <= 0 || entry.getValue().memberCount() != actualCount) {
                fail(ClusteringRunStateCode.RUN_RESULT_MISMATCH);
            }
        }
        sortedMembers.sort((left, right) -> UnicodeCodePointComparator.INSTANCE.compare(
                left.userId(),
                right.userId()
        ));
        TreeSet<String> orderedUserIds = new TreeSet<>(UnicodeCodePointComparator.INSTANCE);
        orderedUserIds.addAll(uniqueUserIds);
        return new NormalizedResult(
                new LinkedHashMap<>(summaries),
                Map.copyOf(actualCounts),
                List.copyOf(sortedMembers),
                List.copyOf(orderedUserIds)
        );
    }

    private Map<String, UserAccount> loadUsers(List<String> userIds) {
        List<UserAccount> users = userRepository.findAllById(userIds);
        Map<String, UserAccount> usersById = new HashMap<>();
        for (UserAccount user : users) {
            usersById.put(user.getId(), user);
        }
        if (usersById.size() != userIds.size()
                || !usersById.keySet().equals(Set.copyOf(userIds))) {
            fail(ClusteringRunStateCode.USER_REFERENCE_MISSING);
        }
        return Map.copyOf(usersById);
    }

    private Map<Integer, Community> buildCommunities(
            ClusteringRun run,
            NormalizedResult normalized
    ) {
        Map<Integer, Community> communities = new LinkedHashMap<>();
        for (Map.Entry<Integer, CommunitySummary> entry : normalized.summaries().entrySet()) {
            int clusterNo = entry.getKey();
            CommunitySummary summary = entry.getValue();
            String description = description(summary.topInterests());
            if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
                fail(ClusteringRunStateCode.RUN_RESULT_MISMATCH);
            }

            Community community = new Community();
            community.setId(identifierGenerator.newCommunityId());
            community.setRun(run);
            community.setClusterNo(clusterNo);
            community.setName("社区 " + (clusterNo + 1));
            community.setDescription(description);
            community.setMemberCount(normalized.actualCounts().get(clusterNo));
            community.setTopInterestsJson(
                    jsonSerializer.topInterestsJson(summary.topInterests())
            );
            community.setColor(DISPLAY_COLORS.get(clusterNo % DISPLAY_COLORS.size()));
            validateIdentifier(community.getId());
            communities.put(clusterNo, community);
        }
        return communities;
    }

    private List<CommunityMember> buildMembers(
            ClusteringRun run,
            List<MemberResult> results,
            Map<Integer, Community> communitiesByCluster,
            Map<String, UserAccount> usersById,
            Instant assignedAt
    ) {
        List<CommunityMember> members = new ArrayList<>(results.size());
        for (MemberResult result : results) {
            Community community = communitiesByCluster.get(result.clusterNo());
            UserAccount user = usersById.get(result.userId());
            if (community == null || user == null || community.getRun() != run) {
                fail(ClusteringRunStateCode.RUN_RESULT_MISMATCH);
            }

            CommunityMember member = new CommunityMember();
            member.setId(identifierGenerator.newMemberId());
            member.setRun(run);
            member.setCommunity(community);
            member.setUser(user);
            member.setCoordinateX(result.coordinateX());
            member.setCoordinateY(result.coordinateY());
            member.setDistanceToCenter(result.distanceToCenter());
            member.setAssignedAt(assignedAt);
            validateIdentifier(member.getId());
            members.add(member);
        }
        return List.copyOf(members);
    }

    private void verifyStoredCounts(
            String runId,
            ValidatedClusteringResult result,
            Map<Integer, Community> communitiesByCluster
    ) {
        if (communityRepository.countByRunId(runId) != result.clusterCount()
                || memberRepository.countByRunId(runId) != result.sampleCount()) {
            fail(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
        }
        for (Community community : communitiesByCluster.values()) {
            if (memberRepository.countByCommunityId(community.getId())
                    != community.getMemberCount()) {
                fail(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
            }
        }
    }

    private static String description(List<String> topInterests) {
        return topInterests.isEmpty()
                ? null
                : "主要兴趣：" + String.join("、", topInterests);
    }

    private static void validatePersistenceTime(ClusteringRun run, Instant persistedAt) {
        if (run.getCreatedAt() == null
                || run.getStartedAt() == null
                || persistedAt.isBefore(run.getCreatedAt())
                || persistedAt.isBefore(run.getStartedAt())) {
            fail(ClusteringRunStateCode.INVALID_STATE_TRANSITION);
        }
    }

    private static void requireRunning(ClusteringRunStatus status) {
        if (status == ClusteringRunStatus.SUCCESS || status == ClusteringRunStatus.FAILED) {
            fail(ClusteringRunStateCode.RUN_ALREADY_TERMINAL);
        }
        if (status != ClusteringRunStatus.RUNNING) {
            fail(ClusteringRunStateCode.INVALID_STATE_TRANSITION);
        }
    }

    private static void validateIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.length() > 64) {
            fail(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
        }
    }

    private static ClusteringRunStateException stateException(ClusteringRunStateCode code) {
        return new ClusteringRunStateException(code);
    }

    private static void fail(ClusteringRunStateCode code) {
        throw stateException(code);
    }

    private record NormalizedResult(
            Map<Integer, CommunitySummary> summaries,
            Map<Integer, Integer> actualCounts,
            List<MemberResult> members,
            List<String> userIds
    ) {
    }
}
