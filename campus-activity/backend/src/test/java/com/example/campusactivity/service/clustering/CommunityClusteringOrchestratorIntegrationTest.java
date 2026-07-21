package com.example.campusactivity.service.clustering;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.client.clustering.dto.ClusteringMetrics;
import com.example.campusactivity.client.clustering.dto.ClusteringRequest;
import com.example.campusactivity.client.clustering.dto.ClusteringResponse;
import com.example.campusactivity.client.clustering.dto.CommunitySummary;
import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.client.clustering.dto.MemberResult;
import com.example.campusactivity.client.clustering.exception.InvalidClusteringServiceResponseException;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.CommunityMemberRepository;
import com.example.campusactivity.repository.CommunityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "community-clustering.python.enabled=true",
        "community-clustering.python.base-url=http://localhost:8000",
        "community-clustering.dispatcher.initial-delay-ms=3600000"
})
@Import(ClusteringPersistenceTestConfig.class)
class CommunityClusteringOrchestratorIntegrationTest {
    @Autowired
    private CommunityClusteringOrchestrator orchestrator;
    @SpyBean
    private CommunityFeatureAggregationService featureAggregationService;
    @SpyBean
    private ClusteringRunLifecycleService lifecycleService;
    @SpyBean
    private ClusteringRunFailureService failureService;
    @MockBean
    private ClusteringClient clusteringClient;
    @SpyBean
    private ClusteringResponseValidator responseValidator;
    @SpyBean
    private ClusteringResultPersistenceService persistenceService;
    @SpyBean
    private ClusteringRunRepository runRepository;
    @SpyBean
    private ClusteringIdentifierGenerator identifierGenerator;
    @Autowired
    private CommunityRepository communityRepository;
    @Autowired
    private CommunityMemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanClusteringData() {
        memberRepository.deleteAllInBatch();
        communityRepository.deleteAllInBatch();
        runRepository.deleteAllInBatch();
    }

    @Test
    void rejectsExecutionInsideExternalTransactionBeforeAnyDependencyInteraction() {
        clearInvocations(
                featureAggregationService,
                lifecycleService,
                failureService,
                clusteringClient,
                responseValidator,
                persistenceService
        );
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        ClusteringExecutionResult result = transaction.execute(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return orchestrator.execute(
                    new CommunityClusteringExecutionCommand(2, "admin-outer-transaction")
            );
        });

        assertThat(result).isEqualTo(ClusteringExecutionResult.preRunFailed(
                ClusteringRunFailureCode.INTERNAL_ERROR,
                0,
                2
        ));
        verifyNoInteractions(
                featureAggregationService,
                lifecycleService,
                failureService,
                clusteringClient,
                responseValidator,
                persistenceService
        );
        assertThat(runRepository.count()).isZero();
    }

    @Test
    void rejectsNullCommandInsideExternalTransactionWithoutBusinessInteraction() {
        clearInvocations(
                featureAggregationService,
                lifecycleService,
                failureService,
                clusteringClient,
                responseValidator,
                persistenceService
        );
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                transaction.execute(status -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isTrue();
                    return orchestrator.execute(null);
                })
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("聚类执行命令不能为空")
                .hasNoCause();

        verifyNoInteractions(
                featureAggregationService,
                lifecycleService,
                failureService,
                clusteringClient,
                responseValidator,
                persistenceService
        );
        assertThat(runRepository.count()).isZero();
    }

    @Test
    void successfulHttpCallRunsWithoutTransactionAndPersistsCompleteResult() {
        when(clusteringClient.runClustering(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return responseFor(invocation.getArgument(0));
        });

        ClusteringExecutionResult result = orchestrator.execute(
                new CommunityClusteringExecutionCommand(2, "admin-integration-success")
        );

        assertThat(result.outcome()).isEqualTo(ClusteringExecutionOutcome.SUCCESS);
        assertThat(result.finalStatus()).isEqualTo(ClusteringRunStatus.SUCCESS);
        assertThat(result.failureCode()).isNull();
        ClusteringRun run = runRepository.findById(result.runId()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ClusteringRunStatus.SUCCESS);
        assertThat(run.getActiveSlot()).isNull();
        assertThat(communityRepository.countByRunId(result.runId())).isEqualTo(2);
        assertThat(memberRepository.countByRunId(result.runId()))
                .isEqualTo(result.sampleCount());
        verify(clusteringClient, times(1)).runClustering(any());
        verify(clusteringClient, times(0)).health();
    }

    @Test
    void persistenceRollbackCompletesBeforeIndependentFailureRecording() {
        when(clusteringClient.runClustering(any())).thenAnswer(invocation ->
                responseFor(invocation.getArgument(0))
        );
        doThrow(new DataAccessResourceFailureException(
                "dynamic database URL and response body"
        )).when(runRepository).flush();
        doAnswer(invocation -> {
            String runId = invocation.getArgument(0);
            assertThat(communityRepository.countByRunId(runId)).isZero();
            assertThat(memberRepository.countByRunId(runId)).isZero();
            ClusteringRun running = runRepository.findById(runId).orElseThrow();
            assertThat(running.getStatus()).isEqualTo(ClusteringRunStatus.RUNNING);
            assertThat(running.getActiveSlot()).isEqualTo(ClusteringRun.GLOBAL_ACTIVE_SLOT);
            return invocation.callRealMethod();
        }).when(failureService).markFailed(
                any(),
                any()
        );

        ClusteringExecutionResult result = orchestrator.execute(
                new CommunityClusteringExecutionCommand(2, "admin-integration-rollback")
        );

        assertThat(result.outcome()).isEqualTo(ClusteringExecutionOutcome.RUN_FAILED);
        assertThat(result.failureCode())
                .isEqualTo(ClusteringRunFailureCode.RESULT_PERSISTENCE_FAILED);
        assertThat(result.failureRecorded()).isTrue();
        assertThat(communityRepository.countByRunId(result.runId())).isZero();
        assertThat(memberRepository.countByRunId(result.runId())).isZero();
        ClusteringRun failed = runRepository.findById(result.runId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ClusteringRunStatus.FAILED);
        assertThat(failed.getActiveSlot()).isNull();
        assertThat(failed.getErrorMessage())
                .isEqualTo(ClusteringRunFailureCode.RESULT_PERSISTENCE_FAILED.errorMessage())
                .doesNotContain("dynamic", "URL", "response body");
    }

    @Test
    void failedNewRunDoesNotModifyHistoricalSuccess() {
        when(clusteringClient.runClustering(any())).thenAnswer(invocation ->
                responseFor(invocation.getArgument(0))
        );
        ClusteringExecutionResult historical = orchestrator.execute(
                new CommunityClusteringExecutionCommand(2, "admin-history-success")
        );
        long historicalCommunityCount = communityRepository.countByRunId(historical.runId());
        long historicalMemberCount = memberRepository.countByRunId(historical.runId());
        ClusteringRun historicalBefore = runRepository.findById(historical.runId()).orElseThrow();
        String historicalMetrics = historicalBefore.getMetricsJson();

        doThrow(new InvalidClusteringServiceResponseException())
                .when(clusteringClient)
                .runClustering(any());
        ClusteringExecutionResult failed = orchestrator.execute(
                new CommunityClusteringExecutionCommand(2, "admin-history-failure")
        );

        assertThat(failed.outcome()).isEqualTo(ClusteringExecutionOutcome.RUN_FAILED);
        assertThat(failed.failureCode())
                .isEqualTo(ClusteringRunFailureCode.PYTHON_PROTOCOL_ERROR);
        ClusteringRun historicalAfter = runRepository.findById(historical.runId()).orElseThrow();
        assertThat(historicalAfter.getStatus()).isEqualTo(ClusteringRunStatus.SUCCESS);
        assertThat(historicalAfter.getMetricsJson()).isEqualTo(historicalMetrics);
        assertThat(communityRepository.countByRunId(historical.runId()))
                .isEqualTo(historicalCommunityCount);
        assertThat(memberRepository.countByRunId(historical.runId()))
                .isEqualTo(historicalMemberCount);
        assertThat(runRepository.findById(failed.runId()).orElseThrow().getStatus())
                .isEqualTo(ClusteringRunStatus.FAILED);
    }

    @Test
    void concurrentExecutionsAllowAtMostOnePythonPost() throws Exception {
        CountDownLatch bothCreating = new CountDownLatch(2);
        AtomicInteger runIdSequence = new AtomicInteger();
        AtomicInteger versionSequence = new AtomicInteger();
        doAnswer(invocation -> {
            String runId = "orchestrator-concurrent-run-" + runIdSequence.incrementAndGet();
            bothCreating.countDown();
            if (!bothCreating.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("concurrent executions did not reach run creation");
            }
            return runId;
        }).when(identifierGenerator).newRunId();
        doAnswer(invocation ->
                "orchestrator-concurrent-version-" + versionSequence.incrementAndGet()
        ).when(identifierGenerator).newVersion();
        when(clusteringClient.runClustering(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return responseFor(invocation.getArgument(0));
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ClusteringExecutionResult> first = executor.submit(() ->
                    orchestrator.execute(new CommunityClusteringExecutionCommand(
                            2,
                            "admin-concurrent-a"
                    ))
            );
            Future<ClusteringExecutionResult> second = executor.submit(() ->
                    orchestrator.execute(new CommunityClusteringExecutionCommand(
                            2,
                            "admin-concurrent-b"
                    ))
            );

            List<ClusteringExecutionResult> results = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            );

            assertThat(results)
                    .as("concurrent results: %s", results)
                    .filteredOn(result -> result.outcome() == ClusteringExecutionOutcome.SUCCESS)
                    .hasSize(1);
            assertThat(results)
                    .filteredOn(result ->
                            result.outcome() == ClusteringExecutionOutcome.PRECONDITION_REJECTED
                    )
                    .hasSize(1)
                    .allSatisfy(result -> assertThat(result.failureCode())
                            .isEqualTo(ClusteringRunFailureCode.ACTIVE_RUN_EXISTS));
            assertThat(runRepository.count()).isEqualTo(1);
            assertThat(runRepository.findAll())
                    .singleElement()
                    .satisfies(run -> {
                        assertThat(run.getStatus()).isEqualTo(ClusteringRunStatus.SUCCESS);
                        assertThat(run.getActiveSlot()).isNull();
                    });
            verify(featureAggregationService, times(2)).aggregateFeatureSamples();
            verify(failureService, never()).markFailed(any(), any());
            verify(clusteringClient, times(1)).runClustering(any());
        } finally {
            executor.shutdownNow();
        }
    }

    private static ClusteringResponse responseFor(ClusteringRequest request) {
        List<FeatureSample> orderedSamples = request.samples().stream()
                .sorted((left, right) -> UnicodeCodePointComparator.INSTANCE.compare(
                        left.userId(),
                        right.userId()
                ))
                .toList();
        Map<String, Integer> clusterByUser = new HashMap<>();
        for (int index = 0; index < orderedSamples.size(); index++) {
            int clusterNo = index == orderedSamples.size() - 1 ? 1 : 0;
            clusterByUser.put(orderedSamples.get(index).userId(), clusterNo);
        }

        List<MemberResult> members = new ArrayList<>();
        for (int index = 0; index < orderedSamples.size(); index++) {
            FeatureSample sample = orderedSamples.get(index);
            members.add(new MemberResult(
                    sample.userId(),
                    clusterByUser.get(sample.userId()),
                    10.0 + index,
                    20.0 + index,
                    0.1 + index
            ));
        }

        List<CommunitySummary> communities = List.of(
                communitySummary(0, orderedSamples, clusterByUser),
                communitySummary(1, orderedSamples, clusterByUser)
        );
        return new ClusteringResponse(
                request.runId(),
                request.version(),
                request.algorithm(),
                request.clusterCount(),
                orderedSamples.size(),
                new ClusteringMetrics(1.0, List.of(0.7, 0.2)),
                communities,
                members
        );
    }

    private static CommunitySummary communitySummary(
            int clusterNo,
            List<FeatureSample> samples,
            Map<String, Integer> clusterByUser
    ) {
        List<FeatureSample> clusterSamples = samples.stream()
                .filter(sample -> clusterByUser.get(sample.userId()) == clusterNo)
                .toList();
        Map<String, Integer> interestCounts = new HashMap<>();
        for (FeatureSample sample : clusterSamples) {
            for (String interest : new HashSet<>(sample.interests())) {
                interestCounts.merge(interest, 1, Integer::sum);
            }
        }
        List<String> topInterests = interestCounts.entrySet().stream()
                .sorted((left, right) -> {
                    int frequency = Integer.compare(right.getValue(), left.getValue());
                    return frequency != 0
                            ? frequency
                            : UnicodeCodePointComparator.INSTANCE.compare(
                                    left.getKey(),
                                    right.getKey()
                            );
                })
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
        return new CommunitySummary(clusterNo, clusterSamples.size(), topInterests);
    }
}
