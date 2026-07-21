package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.CommunityMemberRepository;
import com.example.campusactivity.repository.CommunityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "community-clustering.python.enabled=false")
@Import(ClusteringPersistenceTestConfig.class)
class ClusteringRunLifecycleServiceIntegrationTest {
    @Autowired
    private ClusteringRunLifecycleService lifecycleService;
    @Autowired
    private ClusteringRunFailureService failureService;
    @Autowired
    private ClusteringRunRepository runRepository;
    @Autowired
    private CommunityRepository communityRepository;
    @Autowired
    private CommunityMemberRepository memberRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @SpyBean
    private ClusteringIdentifierGenerator identifierGenerator;
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
    void createsPendingRunWithWhitelistedParametersAndFixedClock() throws Exception {
        ClusteringRunSnapshot created = lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 3, "admin-lifecycle")
        );

        assertThat(AopUtils.isAopProxy(lifecycleService)).isTrue();
        assertThat(created.status()).isEqualTo(ClusteringRunStatus.PENDING);
        assertThat(created.algorithm()).isEqualTo(ClusteringAlgorithm.KMEANS);
        assertThat(created.clusterCount()).isEqualTo(2);
        assertThat(created.sampleCount()).isEqualTo(3);
        assertThat(created.randomState()).isEqualTo(42);
        assertThat(created.featureSchemaVersion()).isEqualTo("community-features-v1");
        assertThat(created.createdAt()).isEqualTo(ClusteringPersistenceTestConfig.FIXED_INSTANT);
        assertThat(created.startedAt()).isNull();
        assertThat(created.finishedAt()).isNull();
        assertThat(created.errorMessage()).isNull();
        assertThat(created.metricsJson()).isNull();
        assertThat(created.runId()).hasSizeLessThanOrEqualTo(64);
        assertThat(created.version()).hasSizeLessThanOrEqualTo(64);
        assertThat(UUID.fromString(created.runId()).toString()).isEqualTo(created.runId());
        assertThat(UUID.fromString(created.version()).toString()).isEqualTo(created.version());
        assertThat(created.parametersJson()).isEqualTo(
                "{\"algorithm\":\"KMEANS\",\"clusterCount\":2,\"randomState\":42,"
                        + "\"featureSchemaVersion\":\"community-features-v1\","
                        + "\"displaySchemaVersion\":\"community-display-v1\"}"
        );

        JsonNode parameters = objectMapper.readTree(created.parametersJson());
        assertThat(List.copyOf(parameters.properties()).stream().map(java.util.Map.Entry::getKey))
                .containsExactly(
                        "algorithm",
                        "clusterCount",
                        "randomState",
                        "featureSchemaVersion",
                        "displaySchemaVersion"
                );
        assertThat(parameters.has("samples")).isFalse();
        assertThat(parameters.has("userId")).isFalse();
        assertThat(parameters.has("baseUrl")).isFalse();
        assertThat(runRepository.count()).isEqualTo(1);
        assertThat(runRepository.findById(created.runId()).orElseThrow().getActiveSlot())
                .isEqualTo(ClusteringRun.GLOBAL_ACTIVE_SLOT);
    }

    @Test
    void rejectsInvalidCreationParametersWithFixedSafeException() {
        List<ClusteringRunCreationCommand> invalidCommands = List.of(
                new ClusteringRunCreationCommand(1, 3, "admin"),
                new ClusteringRunCreationCommand(2, -1, "admin"),
                new ClusteringRunCreationCommand(3, 2, "admin"),
                new ClusteringRunCreationCommand(2, 2, " ")
        );

        for (ClusteringRunCreationCommand command : invalidCommands) {
            assertThatThrownBy(() -> lifecycleService.createPending(command))
                    .isInstanceOfSatisfying(ClusteringRunStateException.class, exception -> {
                        assertThat(exception.getCode())
                                .isEqualTo(ClusteringRunStateCode.INVALID_INITIAL_PARAMETERS);
                        assertThat(exception.getMessage())
                                .isEqualTo(ClusteringRunStateCode.INVALID_INITIAL_PARAMETERS.safeMessage());
                        assertThat(exception.getCause()).isNull();
                    });
        }
        assertThat(runRepository.count()).isZero();
    }

    @Test
    void rejectsSecondActiveRunButAllowsNewIdentifiersAfterFailure() {
        ClusteringRunSnapshot first = lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin")
        );

        assertThatThrownBy(() -> lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin")
        )).isInstanceOfSatisfying(ClusteringRunStateException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(ClusteringRunStateCode.ACTIVE_RUN_EXISTS)
        );

        failureService.markFailed(first.runId(), ClusteringRunFailureCode.UNEXPECTED_INTERNAL_FAILURE);
        assertThat(runRepository.findById(first.runId()).orElseThrow().getActiveSlot()).isNull();
        ClusteringRunSnapshot retry = lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin")
        );
        assertThat(retry.runId()).isNotEqualTo(first.runId());
        assertThat(retry.version()).isNotEqualTo(first.version());
    }

    @Test
    void marksRunningOnlyFromPendingAndPreservesTimeOrder() {
        ClusteringRunSnapshot pending = lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin")
        );

        ClusteringRunSnapshot running = lifecycleService.markRunning(pending.runId());

        assertThat(running.status()).isEqualTo(ClusteringRunStatus.RUNNING);
        assertThat(running.startedAt()).isEqualTo(ClusteringPersistenceTestConfig.FIXED_INSTANT);
        assertThat(running.startedAt()).isAfterOrEqualTo(running.createdAt());
        assertThat(running.finishedAt()).isNull();
        assertThat(running.errorMessage()).isNull();
        assertThat(runRepository.findById(running.runId()).orElseThrow().getActiveSlot())
                .isEqualTo(ClusteringRun.GLOBAL_ACTIVE_SLOT);

        assertThatThrownBy(() -> lifecycleService.markRunning(pending.runId()))
                .isInstanceOfSatisfying(ClusteringRunStateException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ClusteringRunStateCode.INVALID_STATE_TRANSITION)
                );
    }

    @Test
    void refusesTerminalRunsAndMissingRunForMarkRunning() {
        runRepository.saveAndFlush(run("failed-run", "failed-version", ClusteringRunStatus.FAILED));
        runRepository.saveAndFlush(run("success-run", "success-version", ClusteringRunStatus.SUCCESS));

        for (String runId : List.of("failed-run", "success-run")) {
            assertThatThrownBy(() -> lifecycleService.markRunning(runId))
                    .isInstanceOfSatisfying(ClusteringRunStateException.class, exception ->
                            assertThat(exception.getCode())
                                    .isEqualTo(ClusteringRunStateCode.RUN_ALREADY_TERMINAL)
                    );
        }
        assertThatThrownBy(() -> lifecycleService.markRunning("missing-run"))
                .isInstanceOfSatisfying(ClusteringRunStateException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ClusteringRunStateCode.RUN_NOT_FOUND)
                );
    }

    @Test
    void concurrentCreatePendingUsesDatabaseActiveSlotAsFinalGuard() throws Exception {
        ClusteringRun historical = run(
                "historical-concurrent-run",
                "historical-concurrent-version",
                ClusteringRunStatus.SUCCESS
        );
        historical = runRepository.saveAndFlush(historical);
        ClusteringRunSnapshot historicalBefore = ClusteringRunSnapshot.from(historical);

        CountDownLatch bothPassedPrecheck = new CountDownLatch(2);
        AtomicInteger runSequence = new AtomicInteger();
        AtomicInteger versionSequence = new AtomicInteger();
        doAnswer(invocation -> {
            String runId = "concurrent-run-" + runSequence.incrementAndGet();
            bothPassedPrecheck.countDown();
            if (!bothPassedPrecheck.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("concurrent create calls did not both pass the precheck");
            }
            return runId;
        }).when(identifierGenerator).newRunId();
        doAnswer(invocation -> "concurrent-version-" + versionSequence.incrementAndGet())
                .when(identifierGenerator)
                .newVersion();

        CyclicBarrier startBarrier = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CreationAttempt> first = executor.submit(
                    () -> createConcurrently(startBarrier, "admin-concurrent-a")
            );
            Future<CreationAttempt> second = executor.submit(
                    () -> createConcurrently(startBarrier, "admin-concurrent-b")
            );
            startBarrier.await(10, TimeUnit.SECONDS);

            List<CreationAttempt> attempts = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
            assertThat(attempts).filteredOn(attempt -> attempt.run() != null).hasSize(1);
            assertThat(runSequence).hasValue(2);
            assertThat(versionSequence).hasValue(2);
            assertThat(attempts).filteredOn(attempt -> attempt.failure() != null)
                    .singleElement()
                    .satisfies(attempt -> assertThat(attempt.failure())
                            .isInstanceOfSatisfying(
                                    ClusteringRunStateException.class,
                                    exception -> {
                                        assertThat(exception.getCode())
                                                .isEqualTo(ClusteringRunStateCode.RUN_CREATION_CONFLICT);
                                        assertThat(exception.getMessage())
                                                .isEqualTo(ClusteringRunStateCode.RUN_CREATION_CONFLICT.safeMessage());
                                        assertThat(exception.getCause()).isNull();
                                    }
                            ));
        } finally {
            executor.shutdownNow();
        }

        TransactionTemplate freshTransaction = new TransactionTemplate(transactionManager);
        freshTransaction.executeWithoutResult(status -> {
            entityManager.clear();
            assertThat(runRepository.countByStatusIn(EnumSet.of(
                    ClusteringRunStatus.PENDING,
                    ClusteringRunStatus.RUNNING
            ))).isEqualTo(1);
            assertThat(ClusteringRunSnapshot.from(
                    runRepository.findById(historicalBefore.runId()).orElseThrow()
            )).isEqualTo(historicalBefore);
            assertThat(runRepository.findById(historicalBefore.runId()).orElseThrow().getActiveSlot())
                    .isNull();
        });
    }

    private CreationAttempt createConcurrently(CyclicBarrier startBarrier, String createdBy) {
        try {
            startBarrier.await(10, TimeUnit.SECONDS);
            return new CreationAttempt(
                    lifecycleService.createPending(
                            new ClusteringRunCreationCommand(2, 2, createdBy)
                    ),
                    null
            );
        } catch (Throwable failure) {
            return new CreationAttempt(null, failure);
        }
    }

    private static ClusteringRun run(String id, String version, ClusteringRunStatus status) {
        ClusteringRun run = new ClusteringRun();
        run.setId(id);
        run.setVersion(version);
        run.setAlgorithm(ClusteringAlgorithm.KMEANS);
        run.setClusterCount(2);
        run.setRandomState(42);
        run.setStatus(status);
        run.setActiveSlot(
                status == ClusteringRunStatus.PENDING || status == ClusteringRunStatus.RUNNING
                        ? ClusteringRun.GLOBAL_ACTIVE_SLOT
                        : null
        );
        run.setSampleCount(2);
        run.setFeatureSchemaVersion("community-features-v1");
        run.setParametersJson("{}");
        run.setCreatedBy("admin");
        run.setCreatedAt(Instant.parse("2026-07-16T07:00:00Z"));
        if (status != ClusteringRunStatus.PENDING) {
            run.setStartedAt(Instant.parse("2026-07-16T07:30:00Z"));
        }
        if (status == ClusteringRunStatus.FAILED || status == ClusteringRunStatus.SUCCESS) {
            run.setFinishedAt(ClusteringPersistenceTestConfig.FIXED_INSTANT);
        }
        if (status == ClusteringRunStatus.FAILED) {
            run.setErrorMessage("UNEXPECTED_INTERNAL_FAILURE: 聚类运行发生内部失败");
        }
        if (status == ClusteringRunStatus.SUCCESS) {
            run.setMetricsJson("{\"inertia\":0.0,\"pcaExplainedVarianceRatio\":[1.0,0.0]}");
        }
        return run;
    }

    private record CreationAttempt(ClusteringRunSnapshot run, Throwable failure) {
    }
}
