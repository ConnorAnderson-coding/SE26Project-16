package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.repository.ClusteringRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "community-clustering.python.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:clustering_background_test;DB_CLOSE_DELAY=-1"
})
class CommunityClusteringBackgroundIntegrationTest {
    @Autowired
    private ClusteringRunLifecycleService lifecycleService;
    @Autowired
    private ClusteringRunFailureService failureService;
    @Autowired
    private ClusteringRunRepository runRepository;

    @AfterEach
    void cleanDatabase() {
        runRepository.deleteAll();
    }

    @Test
    void twoConcurrentClaimsReserveOnePendingRunExactlyOnce() throws Exception {
        String runId = createPending();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<ClusteringRunSnapshot>> first = threads.submit(() -> claimAfter(start));
            Future<Optional<ClusteringRunSnapshot>> second = threads.submit(() -> claimAfter(start));
            start.countDown();

            List<Optional<ClusteringRunSnapshot>> results = List.of(first.get(), second.get());
            assertThat(results).filteredOn(Optional::isPresent).hasSize(1);
            assertThat(runRepository.findById(runId).orElseThrow().getStatus())
                    .isEqualTo(ClusteringRunStatus.RUNNING);
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void recoveryPreservesPendingRun() {
        String runId = createPending();
        CommunityClusteringStartupRecovery recovery = recovery();

        recovery.recover();

        assertThat(recovery.isComplete()).isTrue();
        assertThat(runRepository.findById(runId).orElseThrow().getStatus())
                .isEqualTo(ClusteringRunStatus.PENDING);
    }

    @Test
    void recoveryFailsInterruptedRunningRunAndIsIdempotent() {
        String runId = createPending();
        var interrupted = runRepository.findById(runId).orElseThrow();
        interrupted.setStatus(ClusteringRunStatus.RUNNING);
        interrupted.setStartedAt(interrupted.getCreatedAt());
        runRepository.saveAndFlush(interrupted);
        CommunityClusteringStartupRecovery recovery = recovery();

        recovery.recover();
        recovery.recover();

        var failed = runRepository.findById(runId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ClusteringRunStatus.FAILED);
        assertThat(failed.getActiveSlot()).isNull();
        assertThat(failed.getFinishedAt()).isNotNull();
        assertThat(failed.getErrorMessage()).isEqualTo(
                ClusteringRunFailureCode.EXECUTION_INTERRUPTED.errorMessage()
        );
    }

    private Optional<ClusteringRunSnapshot> claimAfter(CountDownLatch start)
            throws InterruptedException {
        start.await();
        return lifecycleService.claimNextPending();
    }

    private String createPending() {
        return lifecycleService.createPending(
                new ClusteringRunCreationCommand(2, 2, "admin-1")
        ).runId();
    }

    private CommunityClusteringStartupRecovery recovery() {
        return new CommunityClusteringStartupRecovery(runRepository, failureService);
    }
}
