package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.CommunityMemberRepository;
import com.example.campusactivity.repository.CommunityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "community-clustering.python.enabled=false")
@Import(ClusteringPersistenceTestConfig.class)
class ClusteringRunFailureServiceIntegrationTest {
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
    void recordsFailureFromPendingInRequiresNewTransaction() {
        ClusteringRunSnapshot pending = lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin")
        );

        FailureRecordingResult result = failureService.markFailed(
                pending.runId(),
                ClusteringRunFailureCode.RESULT_PERSISTENCE_FAILED
        );

        assertThat(AopUtils.isAopProxy(failureService)).isTrue();
        assertThat(result.outcome()).isEqualTo(FailureRecordingResult.Outcome.RECORDED);
        ClusteringRunSnapshot failed = result.run().orElseThrow();
        assertThat(failed.status()).isEqualTo(ClusteringRunStatus.FAILED);
        assertThat(failed.startedAt()).isNull();
        assertThat(failed.finishedAt()).isEqualTo(ClusteringPersistenceTestConfig.FIXED_INSTANT);
        assertThat(failed.finishedAt()).isAfterOrEqualTo(failed.createdAt());
        assertThat(failed.errorMessage())
                .isEqualTo("RESULT_PERSISTENCE_FAILED: 聚类结果持久化失败")
                .hasSizeLessThanOrEqualTo(1000)
                .doesNotContain("http", "Exception", "password", "stack");
        assertThat(runRepository.findById(pending.runId()).orElseThrow().getActiveSlot()).isNull();
    }

    @Test
    void recordsFailureFromRunningAndKeepsStartedTime() {
        ClusteringRunSnapshot pending = lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin")
        );
        ClusteringRunSnapshot running = lifecycleService.markRunning(pending.runId());

        FailureRecordingResult result = failureService.markFailed(
                running.runId(),
                ClusteringRunFailureCode.RESULT_VALIDATION_FAILED
        );

        ClusteringRunSnapshot failed = result.run().orElseThrow();
        assertThat(result.outcome()).isEqualTo(FailureRecordingResult.Outcome.RECORDED);
        assertThat(failed.startedAt()).isEqualTo(running.startedAt());
        assertThat(failed.finishedAt()).isAfterOrEqualTo(failed.startedAt());
    }

    @Test
    void alreadyFailedIsIdempotentWithoutRewritingTerminalFields() {
        ClusteringRunSnapshot pending = lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin")
        );
        FailureRecordingResult first = failureService.markFailed(
                pending.runId(),
                ClusteringRunFailureCode.RESULT_VALIDATION_FAILED
        );

        FailureRecordingResult second = failureService.markFailed(
                pending.runId(),
                ClusteringRunFailureCode.UNEXPECTED_INTERNAL_FAILURE
        );

        assertThat(second.outcome()).isEqualTo(FailureRecordingResult.Outcome.ALREADY_FAILED);
        assertThat(second.run().orElseThrow().finishedAt())
                .isEqualTo(first.run().orElseThrow().finishedAt());
        assertThat(second.run().orElseThrow().errorMessage())
                .isEqualTo(first.run().orElseThrow().errorMessage());
    }

    @Test
    void successCannotBeOverwrittenAndMissingRunHasExplicitOutcome() {
        ClusteringRun success = successfulRun();
        runRepository.saveAndFlush(success);

        FailureRecordingResult conflict = failureService.markFailed(
                success.getId(),
                ClusteringRunFailureCode.UNEXPECTED_INTERNAL_FAILURE
        );
        FailureRecordingResult missing = failureService.markFailed(
                "missing-run",
                ClusteringRunFailureCode.UNEXPECTED_INTERNAL_FAILURE
        );

        assertThat(conflict.outcome())
                .isEqualTo(FailureRecordingResult.Outcome.TERMINAL_SUCCESS_CONFLICT);
        ClusteringRun unchanged = runRepository.findById(success.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ClusteringRunStatus.SUCCESS);
        assertThat(unchanged.getFinishedAt()).isEqualTo(success.getFinishedAt());
        assertThat(unchanged.getErrorMessage()).isNull();
        assertThat(missing.outcome()).isEqualTo(FailureRecordingResult.Outcome.RUN_NOT_FOUND);
        assertThat(missing.run()).isEmpty();
    }

    @Test
    void requiresNewFailureRemainsCommittedAfterOuterTransactionRollsBack() {
        ClusteringRunSnapshot pending = lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin-requires-new")
        );

        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
        outerTransaction.executeWithoutResult(status -> {
            FailureRecordingResult recorded = failureService.markFailed(
                    pending.runId(),
                    ClusteringRunFailureCode.UNEXPECTED_INTERNAL_FAILURE
            );
            assertThat(recorded.outcome()).isEqualTo(FailureRecordingResult.Outcome.RECORDED);
            assertThat(recorded.run().orElseThrow().status()).isEqualTo(ClusteringRunStatus.FAILED);
            status.setRollbackOnly();
        });

        TransactionTemplate freshTransaction = new TransactionTemplate(transactionManager);
        freshTransaction.executeWithoutResult(status -> {
            entityManager.clear();
            ClusteringRun failed = runRepository.findById(pending.runId()).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(ClusteringRunStatus.FAILED);
            assertThat(failed.getActiveSlot()).isNull();
            assertThat(failed.getFinishedAt()).isEqualTo(ClusteringPersistenceTestConfig.FIXED_INSTANT);
        });
    }

    private static ClusteringRun successfulRun() {
        ClusteringRun run = new ClusteringRun();
        run.setId("success-failure-guard");
        run.setVersion("success-failure-version");
        run.setAlgorithm(ClusteringAlgorithm.KMEANS);
        run.setClusterCount(2);
        run.setRandomState(42);
        run.setStatus(ClusteringRunStatus.SUCCESS);
        run.setActiveSlot(null);
        run.setSampleCount(2);
        run.setFeatureSchemaVersion("community-features-v1");
        run.setParametersJson("{}");
        run.setMetricsJson("{\"inertia\":0.0,\"pcaExplainedVarianceRatio\":[1.0,0.0]}");
        run.setStartedAt(Instant.parse("2026-07-16T07:30:00Z"));
        run.setFinishedAt(ClusteringPersistenceTestConfig.FIXED_INSTANT);
        run.setCreatedBy("admin");
        run.setCreatedAt(Instant.parse("2026-07-16T07:00:00Z"));
        return run;
    }
}
