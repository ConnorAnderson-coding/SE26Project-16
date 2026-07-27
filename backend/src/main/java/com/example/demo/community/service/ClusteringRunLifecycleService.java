package com.example.demo.community.service;

import com.example.demo.community.client.ClusteringContracts;
import com.example.demo.community.client.ClusteringContracts.CommunitySummary;
import com.example.demo.community.client.ClusteringContracts.MemberResult;
import com.example.demo.community.client.ClusteringContracts.Response;
import com.example.demo.entity.ClusteringAlgorithm;
import com.example.demo.entity.ClusteringRun;
import com.example.demo.entity.ClusteringRunInput;
import com.example.demo.entity.ClusteringRunStatus;
import com.example.demo.entity.Community;
import com.example.demo.entity.CommunityMember;
import com.example.demo.entity.User;
import com.example.demo.repository.ClusteringRunInputRepository;
import com.example.demo.repository.ClusteringRunRepository;
import com.example.demo.repository.CommunityMemberRepository;
import com.example.demo.repository.CommunityRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClusteringRunLifecycleService {

    private static final List<String> COLORS = List.of(
            "#1677FF", "#52C41A", "#FA8C16", "#722ED1", "#13C2C2", "#EB2F96", "#A0D911", "#2F54EB"
    );

    private final ClusteringRunRepository runRepository;
    private final ClusteringRunInputRepository inputRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public ClusteringRunLifecycleService(
            ClusteringRunRepository runRepository,
            ClusteringRunInputRepository inputRepository,
            CommunityRepository communityRepository,
            CommunityMemberRepository memberRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.runRepository = runRepository;
        this.inputRepository = inputRepository;
        this.communityRepository = communityRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public ClusteringRun createPending(
            String createdBy,
            int clusterCount,
            int sampleCount,
            int featureDimension,
            String featureSchemaVersion,
            Map<String, Object> parameters,
            Map<String, Object> featureManifest
    ) {
        if (createdBy == null || createdBy.isBlank() || createdBy.length() > 32
                || clusterCount < 2 || sampleCount < clusterCount || featureDimension < 1
                || featureSchemaVersion == null || featureSchemaVersion.isBlank()
                || parameters == null || featureManifest == null) {
            throw new ClusteringStateException(ClusteringStateException.Code.INVALID_PARAMETERS);
        }
        if (runRepository.existsByStatusIn(EnumSet.of(ClusteringRunStatus.PENDING, ClusteringRunStatus.RUNNING))) {
            throw new ClusteringStateException(ClusteringStateException.Code.ACTIVE_RUN_EXISTS);
        }
        if (!userRepository.existsById(createdBy)) {
            throw new ClusteringStateException(ClusteringStateException.Code.INVALID_PARAMETERS);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ClusteringRun run = new ClusteringRun();
        run.setId(UUID.randomUUID().toString());
        run.setVersion("cc-" + now.toString().replaceAll("[^0-9]", "") + "-" + UUID.randomUUID().toString().substring(0, 8));
        run.setAlgorithm(ClusteringAlgorithm.KMEANS);
        run.setClusterCount(clusterCount);
        run.setRandomState(ClusteringContracts.RANDOM_STATE);
        run.setStatus(ClusteringRunStatus.PENDING);
        run.setActiveSlot(ClusteringRun.GLOBAL_ACTIVE_SLOT);
        run.setSampleCount(sampleCount);
        run.setFeatureDimension(featureDimension);
        run.setFeatureSchemaVersion(featureSchemaVersion);
        run.setParameters(new LinkedHashMap<>(parameters));
        run.setFeatureManifest(new LinkedHashMap<>(featureManifest));
        run.setCreatedBy(createdBy);
        run.setCreatedAt(now);
        try {
            return runRepository.saveAndFlush(run);
        } catch (DataIntegrityViolationException exception) {
            throw new ClusteringStateException(ClusteringStateException.Code.ACTIVE_RUN_EXISTS, exception);
        }
    }

    @Transactional
    public Optional<String> claimNextPending() {
        List<ClusteringRun> candidates = runRepository.findPendingForClaim(PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        ClusteringRun run = candidates.getFirst();
        requireState(run, ClusteringRunStatus.PENDING);
        run.setStatus(ClusteringRunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now(clock));
        run.setFinishedAt(null);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        runRepository.flush();
        return Optional.of(run.getId());
    }

    @Transactional
    public void complete(String runId, Response response) {
        ClusteringRun run = lockedRun(runId);
        requireState(run, ClusteringRunStatus.RUNNING);
        validateResponse(run, response);

        Map<Integer, CommunitySummary> summaries = response.communities().stream()
                .collect(Collectors.toMap(CommunitySummary::clusterNo, Function.identity()));
        Map<String, User> users = userRepository.findAllById(
                        response.members().stream().map(MemberResult::userId).toList()
                ).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        if (users.size() != response.sampleCount()) {
            invalidRemoteResult();
        }

        List<ClusteringRunInput> inputs = inputRepository.findByRunIdOrderBySampleOrderAsc(runId);
        Set<String> inputUserIds = inputs.stream().map(input -> input.getUser().getId()).collect(Collectors.toSet());
        if (inputs.size() != response.sampleCount() || !inputUserIds.equals(users.keySet())) {
            invalidRemoteResult();
        }

        Map<Integer, Community> communities = new HashMap<>();
        for (int clusterNo = 0; clusterNo < run.getClusterCount(); clusterNo++) {
            CommunitySummary summary = summaries.get(clusterNo);
            Community community = new Community();
            community.setId(runId + "-community-" + clusterNo);
            community.setRun(run);
            community.setClusterNo(clusterNo);
            community.setName("社区 " + (clusterNo + 1));
            community.setDescription(summary.topInterests().isEmpty()
                    ? "基于校园活动参与特征形成的社区"
                    : "代表兴趣：" + String.join("、", summary.topInterests()));
            community.setMemberCount(summary.memberCount());
            community.setTopInterests(summary.topInterests());
            community.setColor(COLORS.get(clusterNo % COLORS.size()));
            communities.put(clusterNo, community);
        }
        communities = communityRepository.saveAllAndFlush(communities.values()).stream()
                .collect(Collectors.toMap(Community::getClusterNo, Function.identity()));

        List<CommunityMember> memberships = new ArrayList<>();
        for (MemberResult result : response.members()) {
            CommunityMember membership = new CommunityMember();
            membership.setRun(run);
            membership.setCommunity(communities.get(result.clusterNo()));
            membership.setUser(users.get(result.userId()));
            membership.setCoordinateX(result.coordinateX());
            membership.setCoordinateY(result.coordinateY());
            membership.setDistanceToCenter(result.distanceToCenter());
            membership.setAssignedAt(LocalDateTime.now(clock));
            memberships.add(membership);
        }
        memberRepository.saveAll(memberships);

        run.setMetrics(Map.of(
                "inertia", response.metrics().inertia(),
                "pcaExplainedVarianceRatio", response.metrics().pcaExplainedVarianceRatio()
        ));
        run.setStatus(ClusteringRunStatus.SUCCESS);
        run.setActiveSlot(null);
        run.setFinishedAt(LocalDateTime.now(clock));
        run.setErrorCode(null);
        run.setErrorMessage(null);
        runRepository.flush();
    }

    @Transactional
    public boolean markFailed(String runId, String errorCode, String errorMessage) {
        ClusteringRun run = runRepository.findByIdForUpdate(runId).orElse(null);
        if (run == null) {
            return false;
        }
        if (run.getStatus() == ClusteringRunStatus.SUCCESS || run.getStatus() == ClusteringRunStatus.FAILED) {
            return false;
        }
        run.setStatus(ClusteringRunStatus.FAILED);
        run.setActiveSlot(null);
        run.setFinishedAt(LocalDateTime.now(clock));
        run.setErrorCode(truncate(errorCode, 64));
        run.setErrorMessage(truncate(errorMessage, 1000));
        runRepository.flush();
        return true;
    }

    @Transactional
    public int recoverInterruptedRuns() {
        int recovered = 0;
        for (String runId : runRepository.findIdsByStatusOrderByCreatedAtAscIdAsc(ClusteringRunStatus.RUNNING)) {
            if (markFailed(runId, "EXECUTION_INTERRUPTED", "应用重启时检测到未完成的聚类运行")) {
                recovered++;
            }
        }
        return recovered;
    }

    private void validateResponse(ClusteringRun run, Response response) {
        if (response == null || !run.getId().equals(response.runId()) || !run.getVersion().equals(response.version())
                || !ClusteringContracts.ALGORITHM.equals(response.algorithm())
                || !run.getClusterCount().equals(response.clusterCount())
                || !run.getSampleCount().equals(response.sampleCount())
                || response.communities().size() != run.getClusterCount()
                || response.members().size() != run.getSampleCount()) {
            invalidRemoteResult();
        }
        Set<Integer> clusterNumbers = new HashSet<>();
        int reportedMembers = 0;
        for (CommunitySummary summary : response.communities()) {
            if (summary.clusterNo() >= run.getClusterCount() || !clusterNumbers.add(summary.clusterNo())) {
                invalidRemoteResult();
            }
            reportedMembers += summary.memberCount();
        }
        if (reportedMembers != run.getSampleCount() || clusterNumbers.size() != run.getClusterCount()) {
            invalidRemoteResult();
        }
        Map<Integer, Long> actualCounts = response.members().stream()
                .peek(member -> {
                    if (member.clusterNo() >= run.getClusterCount()) {
                        invalidRemoteResult();
                    }
                })
                .collect(Collectors.groupingBy(MemberResult::clusterNo, Collectors.counting()));
        for (CommunitySummary summary : response.communities()) {
            if (actualCounts.getOrDefault(summary.clusterNo(), 0L) != summary.memberCount().longValue()) {
                invalidRemoteResult();
            }
        }
    }

    private ClusteringRun lockedRun(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new ClusteringStateException(ClusteringStateException.Code.RUN_NOT_FOUND);
        }
        return runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new ClusteringStateException(ClusteringStateException.Code.RUN_NOT_FOUND));
    }

    private static void requireState(ClusteringRun run, ClusteringRunStatus expected) {
        if (run.getStatus() != expected) {
            throw new ClusteringStateException(ClusteringStateException.Code.INVALID_STATE_TRANSITION);
        }
    }

    private static void invalidRemoteResult() {
        throw new ClusteringStateException(ClusteringStateException.Code.INVALID_REMOTE_RESULT);
    }

    private static String truncate(String value, int maximum) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
