package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.dto.ClusteringMetrics;
import com.example.campusactivity.client.clustering.dto.CommunitySummary;
import com.example.campusactivity.client.clustering.dto.MemberResult;
import com.example.campusactivity.entity.ClusteringAlgorithm;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = "community-clustering.python.enabled=false")
@Import(ClusteringPersistenceTestConfig.class)
class ClusteringResultPersistenceServiceIntegrationTest {
    @Autowired
    private ClusteringRunLifecycleService lifecycleService;
    @Autowired
    private ClusteringRunFailureService failureService;
    @Autowired
    private ClusteringResultPersistenceService persistenceService;
    @SpyBean
    private ClusteringRunRepository runRepository;
    @Autowired
    private CommunityRepository communityRepository;
    @Autowired
    private CommunityMemberRepository memberRepository;
    @Autowired
    private UserRepository userRepository;
    @SpyBean
    private ClusteringIdentifierGenerator identifierGenerator;
    @SpyBean
    private ClusteringPersistenceJsonSerializer jsonSerializer;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void cleanClusteringData() {
        memberRepository.deleteAllInBatch();
        communityRepository.deleteAllInBatch();
        runRepository.deleteAllInBatch();
    }

    @Test
    void persistsCompleteSuccessAtomicallyWithDocumentedDisplayMapping() {
        List<String> userIds = List.of("persist-user-a", "persist-user-b", "persist-user-c");
        saveUsers(userIds);
        UserAccount original = userRepository.findById(userIds.get(0)).orElseThrow();
        original.setCollege("软件学院");
        original.setGrade("2024级");
        original.setInterests(List.of("AI", "编程"));
        original.setAvailableTime(List.of("weekend"));
        userRepository.saveAndFlush(original);

        ClusteringRunSnapshot running = newRunningRun(2, userIds.size(), "admin-success");
        ValidatedClusteringResult result = validResult(running, userIds);

        ClusteringRunSnapshot success = persistenceService.persistSuccess(running.runId(), result);

        assertThat(AopUtils.isAopProxy(persistenceService)).isTrue();
        assertThat(success.status()).isEqualTo(ClusteringRunStatus.SUCCESS);
        assertThat(success.finishedAt()).isEqualTo(ClusteringPersistenceTestConfig.FIXED_INSTANT);
        assertThat(success.finishedAt()).isAfterOrEqualTo(success.startedAt());
        assertThat(success.errorMessage()).isNull();
        assertThat(success.metricsJson()).isEqualTo(
                "{\"inertia\":12.5,\"pcaExplainedVarianceRatio\":[0.7,0.2]}"
        );

        ClusteringRun run = runRepository.findById(running.runId()).orElseThrow();
        assertThat(run.getActiveSlot()).isNull();
        List<Community> communities = communityRepository.findByRunOrderByClusterNoAsc(run);
        assertThat(communities).hasSize(2);
        assertThat(communities).extracting(Community::getClusterNo).containsExactly(0, 1);
        assertThat(communities).extracting(Community::getName)
                .containsExactly("社区 1", "社区 2");
        assertThat(communities).extracting(Community::getDescription)
                .containsExactly("主要兴趣：AI、编程", null);
        assertThat(communities).extracting(Community::getColor)
                .containsExactly("#1677FF", "#52C41A");
        assertThat(communities).extracting(Community::getMemberCount)
                .containsExactly(2, 1);
        assertThat(communities).extracting(Community::getTopInterestsJson)
                .containsExactly("[\"AI\",\"编程\"]", "[]");
        assertThat(communityRepository.countByRunId(run.getId())).isEqualTo(2);

        List<CommunityMember> members = memberRepository.findByRun(run);
        assertThat(members).hasSize(3);
        assertThat(memberRepository.countByRunId(run.getId())).isEqualTo(3);
        assertThat(members).extracting(CommunityMember::getAssignedAt)
                .containsOnly(ClusteringPersistenceTestConfig.FIXED_INSTANT);
        assertThat(members).allSatisfy(member -> {
            assertThat(member.getRun().getId()).isEqualTo(member.getCommunity().getRun().getId());
            assertThat(member.getAssignedAt()).isEqualTo(success.finishedAt());
        });

        UserAccount unchanged = userRepository.findById(userIds.get(0)).orElseThrow();
        assertThat(unchanged.getPassword()).isEqualTo("test-password");
        assertThat(unchanged.getName()).isEqualTo("测试用户-persist-user-a");
        assertThat(unchanged.getCollege()).isEqualTo("软件学院");
        assertThat(unchanged.getGrade()).isEqualTo("2024级");
        assertThat(userRepository.findAllInterestValues())
                .filteredOn(value -> value.getUserId().equals(userIds.get(0)))
                .extracting(value -> value.getCollectionValue())
                .containsExactlyInAnyOrder("AI", "编程");
        assertThat(userRepository.findAllAvailableTimeValues())
                .filteredOn(value -> value.getUserId().equals(userIds.get(0)))
                .extracting(value -> value.getCollectionValue())
                .containsExactly("weekend");

        ClusteringRunSnapshot nextPending = lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin-after-success")
        );
        assertThat(nextPending.status()).isEqualTo(ClusteringRunStatus.PENDING);
        assertThat(runRepository.findById(nextPending.runId()).orElseThrow().getActiveSlot())
                .isEqualTo(ClusteringRun.GLOBAL_ACTIVE_SLOT);
    }

    @Test
    void createsMembersInUnicodeCodePointOrder() {
        String bmpUser = "order-user-\uE000";
        String supplementaryUser = "order-user-\uD800\uDC00";
        String lastUser = "order-user-z";
        List<String> userIds = List.of(supplementaryUser, bmpUser, lastUser);
        saveUsers(userIds);
        ClusteringRunSnapshot running = newRunningRun(2, 3, "admin-order");

        String firstId = "00000000-0000-4000-8000-000000000001";
        String secondId = "00000000-0000-4000-8000-000000000002";
        String thirdId = "00000000-0000-4000-8000-000000000003";
        doReturn(firstId, secondId, thirdId).when(identifierGenerator).newMemberId();

        persistenceService.persistSuccess(running.runId(), validResult(running, userIds));

        Map<String, String> memberIdByUser = new HashMap<>();
        ClusteringRun run = runRepository.findById(running.runId()).orElseThrow();
        for (CommunityMember member : memberRepository.findByRun(run)) {
            memberIdByUser.put(member.getUser().getId(), member.getId());
        }
        assertThat(memberIdByUser.get(lastUser)).isEqualTo(firstId);
        assertThat(memberIdByUser.get(bmpUser)).isEqualTo(secondId);
        assertThat(memberIdByUser.get(supplementaryUser)).isEqualTo(thirdId);
    }

    @Test
    void rejectsEveryRunMetadataMismatchWithoutSavingResults() {
        List<String> userIds = List.of("mismatch-a", "mismatch-b", "mismatch-c");
        saveUsers(userIds);
        ClusteringRunSnapshot running = newRunningRun(2, 3, "admin-mismatch");
        ValidatedClusteringResult valid = validResult(running, userIds);

        List<ValidatedClusteringResult> mismatches = List.of(
                copyWithMetadata(valid, "different-run", valid.version(), valid.algorithm(), 2, 3),
                copyWithMetadata(valid, valid.runId(), "different-version", valid.algorithm(), 2, 3),
                copyWithMetadata(valid, valid.runId(), valid.version(), "OTHER", 2, 3),
                copyWithMetadata(valid, valid.runId(), valid.version(), valid.algorithm(), 3, 3),
                copyWithMetadata(valid, valid.runId(), valid.version(), valid.algorithm(), 2, 4)
        );

        for (ValidatedClusteringResult mismatch : mismatches) {
            assertThatThrownBy(() -> persistenceService.persistSuccess(running.runId(), mismatch))
                    .isInstanceOfSatisfying(ClusteringRunStateException.class, exception -> {
                        assertThat(exception.getCode())
                                .isEqualTo(ClusteringRunStateCode.RUN_RESULT_MISMATCH);
                        assertThat(exception.getCause()).isNull();
                    });
            assertNoResultsAndStatus(running.runId(), ClusteringRunStatus.RUNNING);
        }
    }

    @Test
    void missingUserRollsBackAllResults() {
        List<String> userIds = List.of("missing-user-a", "missing-user-b", "missing-user-c");
        saveUsers(userIds.subList(0, 2));
        ClusteringRunSnapshot running = newRunningRun(2, 3, "admin-missing-user");

        assertThatThrownBy(() -> persistenceService.persistSuccess(
                running.runId(),
                validResult(running, userIds)
        )).isInstanceOfSatisfying(ClusteringRunStateException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(ClusteringRunStateCode.USER_REFERENCE_MISSING)
        );

        assertNoResultsAndStatus(running.runId(), ClusteringRunStatus.RUNNING);
    }

    @Test
    void existingResultsAreNeverDeletedOrOverwritten() {
        List<String> userIds = List.of("existing-a", "existing-b", "existing-c");
        saveUsers(userIds);
        ClusteringRunSnapshot running = newRunningRun(2, 3, "admin-existing");
        ClusteringRun run = runRepository.findById(running.runId()).orElseThrow();
        Community existing = community("existing-community", run, 0, 1);
        communityRepository.saveAndFlush(existing);

        assertThatThrownBy(() -> persistenceService.persistSuccess(
                running.runId(),
                validResult(running, userIds)
        )).isInstanceOfSatisfying(ClusteringRunStateException.class, exception ->
                assertThat(exception.getCode())
                        .isEqualTo(ClusteringRunStateCode.RUN_RESULTS_ALREADY_EXIST)
        );

        assertThat(communityRepository.countByRunId(running.runId())).isEqualTo(1);
        assertThat(communityRepository.findById(existing.getId())).isPresent();
        assertThat(memberRepository.countByRunId(running.runId())).isZero();
        assertThat(runRepository.findById(running.runId()).orElseThrow().getStatus())
                .isEqualTo(ClusteringRunStatus.RUNNING);
    }

    @Test
    void nonRunningRunsCannotPersistSuccess() {
        List<String> userIds = List.of("state-a", "state-b", "state-c");
        saveUsers(userIds);
        ClusteringRunSnapshot pending = lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 3, "admin-state")
        );

        assertThatThrownBy(() -> persistenceService.persistSuccess(
                pending.runId(),
                validResult(pending, userIds)
        )).isInstanceOfSatisfying(ClusteringRunStateException.class, exception ->
                assertThat(exception.getCode())
                        .isEqualTo(ClusteringRunStateCode.INVALID_STATE_TRANSITION)
        );

        failureService.markFailed(pending.runId(), ClusteringRunFailureCode.RESULT_VALIDATION_FAILED);
        assertThatThrownBy(() -> persistenceService.persistSuccess(
                pending.runId(),
                validResult(pending, userIds)
        )).isInstanceOfSatisfying(ClusteringRunStateException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(ClusteringRunStateCode.RUN_ALREADY_TERMINAL)
        );
        assertThat(communityRepository.countByRunId(pending.runId())).isZero();
        assertThat(memberRepository.countByRunId(pending.runId())).isZero();
    }

    @Test
    void finalRunFlushFailureRollsBackAllResultsThenFailureCommitsIndependently() {
        List<String> failingUsers = List.of("rollback-a", "rollback-b", "rollback-c");
        saveUsers(failingUsers);
        ClusteringRunSnapshot failingRun = newRunningRun(2, 3, "admin-rollback");
        doThrow(new DataAccessResourceFailureException(
                "SECRET database URL and response body must never escape"
        )).when(runRepository).flush();

        assertThatThrownBy(() -> persistenceService.persistSuccess(
                failingRun.runId(),
                validResult(failingRun, failingUsers)
        )).isInstanceOfSatisfying(ClusteringRunStateException.class, exception -> {
            assertThat(exception.getCode())
                    .isEqualTo(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
            assertThat(exception.getMessage())
                    .isEqualTo(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED.safeMessage())
                    .doesNotContain("SECRET", "URL", "response body");
            assertThat(exception.getCause()).isNull();
        });

        assertNoResultsAndRunState(
                failingRun.runId(),
                ClusteringRunStatus.RUNNING,
                ClusteringRun.GLOBAL_ACTIVE_SLOT
        );
        FailureRecordingResult failure = failureService.markFailed(
                failingRun.runId(),
                ClusteringRunFailureCode.RESULT_PERSISTENCE_FAILED
        );
        assertThat(failure.outcome()).isEqualTo(FailureRecordingResult.Outcome.RECORDED);
        assertFailedWithReleasedSlot(failingRun.runId());
        assertThat(failingUsers).allSatisfy(userId ->
                assertThat(userRepository.findById(userId)).isPresent()
        );
    }

    @Test
    void runIdCollisionFailsInsertAndPreservesHistoricalSuccessDeeply() {
        HistoricalFixture historical = createHistoricalSuccess("run-id-collision-history");
        doReturn(historical.state().run().id()).when(identifierGenerator).newRunId();

        assertThatThrownBy(() -> lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin-run-id-collision")
        )).isInstanceOfSatisfying(ClusteringRunStateException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo(ClusteringRunStateCode.RUN_CREATION_CONFLICT);
            assertThat(exception.getMessage())
                    .isEqualTo(ClusteringRunStateCode.RUN_CREATION_CONFLICT.safeMessage());
            assertThat(exception.getCause()).isNull();
        });

        assertThat(readHistoricalState(historical.runId())).isEqualTo(historical.state());
        assertThat(runRepository.count()).isEqualTo(1);
    }

    @Test
    void versionCollisionFailsInsertAndPreservesHistoricalSuccessDeeply() {
        HistoricalFixture historical = createHistoricalSuccess("version-collision-history");
        doReturn("new-run-for-version-collision").when(identifierGenerator).newRunId();
        doReturn(historical.state().run().version()).when(identifierGenerator).newVersion();

        assertThatThrownBy(() -> lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin-version-collision")
        )).isInstanceOfSatisfying(ClusteringRunStateException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo(ClusteringRunStateCode.RUN_CREATION_CONFLICT);
            assertThat(exception.getCause()).isNull();
        });

        assertThat(readHistoricalState(historical.runId())).isEqualTo(historical.state());
        assertThat(runRepository.findById("new-run-for-version-collision")).isEmpty();
    }

    @Test
    void communityIdCollisionRollsBackNewResultsAndPreservesHistoricalSuccessDeeply() {
        HistoricalFixture historical = createHistoricalSuccess("community-collision-history");
        List<String> userIds = List.of(
                "community-collision-new-a",
                "community-collision-new-b",
                "community-collision-new-c"
        );
        saveUsers(userIds);
        ClusteringRunSnapshot running = newRunningRun(2, 3, "admin-community-collision");
        doReturn(
                historical.state().communities().get(0).id(),
                "new-community-after-collision"
        ).when(identifierGenerator).newCommunityId();

        assertThatThrownBy(() -> persistenceService.persistSuccess(
                running.runId(),
                validResult(running, userIds)
        )).isInstanceOfSatisfying(ClusteringRunStateException.class, exception -> {
            assertThat(exception.getCode())
                    .isEqualTo(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
            assertThat(exception.getCause()).isNull();
        });

        assertNoResultsAndRunState(
                running.runId(),
                ClusteringRunStatus.RUNNING,
                ClusteringRun.GLOBAL_ACTIVE_SLOT
        );
        assertThat(readHistoricalState(historical.runId())).isEqualTo(historical.state());
    }

    @Test
    void memberIdCollisionRollsBackNewResultsAndPreservesHistoricalMemberDeeply() {
        HistoricalFixture historical = createHistoricalSuccess("member-collision-history");
        List<String> userIds = List.of(
                "member-collision-new-a",
                "member-collision-new-b",
                "member-collision-new-c"
        );
        saveUsers(userIds);
        ClusteringRunSnapshot running = newRunningRun(2, 3, "admin-member-collision");
        doReturn(
                historical.state().members().get(0).id(),
                "new-member-after-collision-1",
                "new-member-after-collision-2"
        ).when(identifierGenerator).newMemberId();

        assertThatThrownBy(() -> persistenceService.persistSuccess(
                running.runId(),
                validResult(running, userIds)
        )).isInstanceOfSatisfying(ClusteringRunStateException.class, exception -> {
            assertThat(exception.getCode())
                    .isEqualTo(ClusteringRunStateCode.RESULT_PERSISTENCE_FAILED);
            assertThat(exception.getCause()).isNull();
        });

        assertNoResultsAndRunState(
                running.runId(),
                ClusteringRunStatus.RUNNING,
                ClusteringRun.GLOBAL_ACTIVE_SLOT
        );
        assertThat(readHistoricalState(historical.runId())).isEqualTo(historical.state());
    }

    @Test
    void metricsSerializationFailureUsesSafeExceptionAndLeavesRunRunning() {
        List<String> userIds = List.of("json-a", "json-b", "json-c");
        saveUsers(userIds);
        ClusteringRunSnapshot running = newRunningRun(2, 3, "admin-json");
        doThrow(new ClusteringRunStateException(ClusteringRunStateCode.RESULT_SERIALIZATION_FAILED))
                .when(jsonSerializer)
                .metricsJson(ArgumentMatchers.any(ClusteringMetrics.class));

        assertThatThrownBy(() -> persistenceService.persistSuccess(
                running.runId(),
                validResult(running, userIds)
        )).isInstanceOfSatisfying(ClusteringRunStateException.class, exception -> {
            assertThat(exception.getCode())
                    .isEqualTo(ClusteringRunStateCode.RESULT_SERIALIZATION_FAILED);
            assertThat(exception.getCause()).isNull();
        });
        assertNoResultsAndStatus(running.runId(), ClusteringRunStatus.RUNNING);
    }

    private ClusteringRunSnapshot newRunningRun(int clusterCount, int sampleCount, String createdBy) {
        ClusteringRunSnapshot pending = lifecycleService.createPending(
                new ClusteringRunCreationCommand(clusterCount, sampleCount, createdBy)
        );
        return lifecycleService.markRunning(pending.runId());
    }

    private HistoricalFixture createHistoricalSuccess(String prefix) {
        List<String> userIds = List.of(
                prefix + "-user-a",
                prefix + "-user-b",
                prefix + "-user-c"
        );
        saveUsers(userIds);
        ClusteringRunSnapshot running = newRunningRun(2, 3, prefix + "-admin");
        ClusteringRunSnapshot success = persistenceService.persistSuccess(
                running.runId(),
                validResult(running, userIds)
        );
        return new HistoricalFixture(success.runId(), readHistoricalState(success.runId()));
    }

    private HistoricalState readHistoricalState(String runId) {
        TransactionTemplate freshTransaction = new TransactionTemplate(transactionManager);
        return freshTransaction.execute(status -> {
            entityManager.clear();
            ClusteringRun run = runRepository.findById(runId).orElseThrow();
            RunState runState = new RunState(
                    run.getId(),
                    run.getVersion(),
                    run.getStatus(),
                    run.getActiveSlot(),
                    run.getAlgorithm(),
                    run.getClusterCount(),
                    run.getRandomState(),
                    run.getSampleCount(),
                    run.getFeatureSchemaVersion(),
                    run.getParametersJson(),
                    run.getMetricsJson(),
                    run.getCreatedAt(),
                    run.getStartedAt(),
                    run.getFinishedAt(),
                    run.getErrorMessage(),
                    run.getCreatedBy()
            );
            List<CommunityState> communities = communityRepository
                    .findByRunOrderByClusterNoAsc(run)
                    .stream()
                    .map(community -> new CommunityState(
                            community.getId(),
                            community.getRun().getId(),
                            community.getClusterNo(),
                            community.getName(),
                            community.getDescription(),
                            community.getMemberCount(),
                            community.getTopInterestsJson(),
                            community.getColor()
                    ))
                    .toList();
            List<MemberState> members = memberRepository.findByRun(run).stream()
                    .map(member -> new MemberState(
                            member.getId(),
                            member.getRun().getId(),
                            member.getCommunity().getId(),
                            member.getUser().getId(),
                            member.getCoordinateX(),
                            member.getCoordinateY(),
                            member.getDistanceToCenter(),
                            member.getAssignedAt()
                    ))
                    .sorted(Comparator.comparing(MemberState::id))
                    .toList();
            return new HistoricalState(runState, communities, members);
        });
    }

    private void assertNoResultsAndStatus(String runId, ClusteringRunStatus status) {
        assertThat(communityRepository.countByRunId(runId)).isZero();
        assertThat(memberRepository.countByRunId(runId)).isZero();
        assertThat(runRepository.findById(runId).orElseThrow().getStatus()).isEqualTo(status);
    }

    private void assertNoResultsAndRunState(
            String runId,
            ClusteringRunStatus status,
            String activeSlot
    ) {
        TransactionTemplate freshTransaction = new TransactionTemplate(transactionManager);
        freshTransaction.executeWithoutResult(transactionStatus -> {
            entityManager.clear();
            assertThat(communityRepository.countByRunId(runId)).isZero();
            assertThat(memberRepository.countByRunId(runId)).isZero();
            ClusteringRun run = runRepository.findById(runId).orElseThrow();
            assertThat(run.getStatus()).isEqualTo(status);
            assertThat(run.getActiveSlot()).isEqualTo(activeSlot);
            assertThat(run.getMetricsJson()).isNull();
            assertThat(run.getFinishedAt()).isNull();
            assertThat(run.getErrorMessage()).isNull();
        });
    }

    private void assertFailedWithReleasedSlot(String runId) {
        TransactionTemplate freshTransaction = new TransactionTemplate(transactionManager);
        freshTransaction.executeWithoutResult(transactionStatus -> {
            entityManager.clear();
            assertThat(communityRepository.countByRunId(runId)).isZero();
            assertThat(memberRepository.countByRunId(runId)).isZero();
            ClusteringRun run = runRepository.findById(runId).orElseThrow();
            assertThat(run.getStatus()).isEqualTo(ClusteringRunStatus.FAILED);
            assertThat(run.getActiveSlot()).isNull();
            assertThat(run.getMetricsJson()).isNull();
            assertThat(run.getFinishedAt()).isEqualTo(ClusteringPersistenceTestConfig.FIXED_INSTANT);
            assertThat(run.getErrorMessage())
                    .isEqualTo("RESULT_PERSISTENCE_FAILED: 聚类结果持久化失败");
        });
    }

    private void saveUsers(List<String> userIds) {
        List<UserAccount> users = new ArrayList<>();
        for (String userId : userIds) {
            UserAccount user = new UserAccount();
            user.setId(userId);
            user.setPassword("test-password");
            user.setName("测试用户-" + userId);
            users.add(user);
        }
        userRepository.saveAllAndFlush(users);
    }

    private static ValidatedClusteringResult validResult(
            ClusteringRunSnapshot run,
            List<String> userIds
    ) {
        List<MemberResult> members = List.of(
                new MemberResult(userIds.get(0), 0, 10.0, 20.0, 0.1),
                new MemberResult(userIds.get(1), 0, 30.0, 40.0, 0.2),
                new MemberResult(userIds.get(2), 1, 90.0, 80.0, 0.3)
        );
        Map<Integer, List<MemberResult>> groups = Map.of(
                0, List.of(members.get(0), members.get(1)),
                1, List.of(members.get(2))
        );
        return new ValidatedClusteringResult(
                run.runId(),
                run.version(),
                run.algorithm().name(),
                2,
                3,
                new ClusteringMetrics(12.5, List.of(0.7, 0.2)),
                List.of(
                        new CommunitySummary(0, 2, List.of("AI", "编程")),
                        new CommunitySummary(1, 1, List.of())
                ),
                members,
                groups
        );
    }

    private static ValidatedClusteringResult copyWithMetadata(
            ValidatedClusteringResult source,
            String runId,
            String version,
            String algorithm,
            int clusterCount,
            int sampleCount
    ) {
        return new ValidatedClusteringResult(
                runId,
                version,
                algorithm,
                clusterCount,
                sampleCount,
                source.metrics(),
                source.communities(),
                source.members(),
                source.membersByClusterNo()
        );
    }

    private static Community community(
            String id,
            ClusteringRun run,
            int clusterNo,
            int memberCount
    ) {
        Community community = new Community();
        community.setId(id);
        community.setRun(run);
        community.setClusterNo(clusterNo);
        community.setName("社区 " + (clusterNo + 1));
        community.setMemberCount(memberCount);
        community.setTopInterestsJson("[]");
        community.setColor("#1677FF");
        return community;
    }

    private record HistoricalFixture(String runId, HistoricalState state) {
    }

    private record HistoricalState(
            RunState run,
            List<CommunityState> communities,
            List<MemberState> members
    ) {
    }

    private record RunState(
            String id,
            String version,
            ClusteringRunStatus status,
            String activeSlot,
            ClusteringAlgorithm algorithm,
            Integer clusterCount,
            Integer randomState,
            Integer sampleCount,
            String featureSchemaVersion,
            String parametersJson,
            String metricsJson,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String errorMessage,
            String createdBy
    ) {
    }

    private record CommunityState(
            String id,
            String runId,
            Integer clusterNo,
            String name,
            String description,
            Integer memberCount,
            String topInterestsJson,
            String color
    ) {
    }

    private record MemberState(
            String id,
            String runId,
            String communityId,
            String userId,
            Double coordinateX,
            Double coordinateY,
            Double distanceToCenter,
            Instant assignedAt
    ) {
    }
}
