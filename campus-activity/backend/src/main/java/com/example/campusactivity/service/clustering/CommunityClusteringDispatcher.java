package com.example.campusactivity.service.clustering;

import com.example.campusactivity.config.CommunityClusteringBackgroundConfig;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        prefix = "community-clustering.python",
        name = "enabled",
        havingValue = "true"
)
public class CommunityClusteringDispatcher {
    private final ClusteringRunLifecycleService lifecycleService;
    private final CommunityClusteringRunExecutor runExecutor;
    private final ClusteringRunFailureService failureService;
    private final CommunityClusteringStartupRecovery startupRecovery;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public CommunityClusteringDispatcher(
            ClusteringRunLifecycleService lifecycleService,
            CommunityClusteringRunExecutor runExecutor,
            ClusteringRunFailureService failureService,
            CommunityClusteringStartupRecovery startupRecovery,
            @Qualifier(CommunityClusteringBackgroundConfig.EXECUTOR_BEAN)
            ThreadPoolTaskExecutor taskExecutor
    ) {
        this.lifecycleService = lifecycleService;
        this.runExecutor = runExecutor;
        this.failureService = failureService;
        this.startupRecovery = startupRecovery;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(
            fixedDelayString = "${community-clustering.dispatcher.poll-interval-ms:1000}",
            initialDelayString = "${community-clustering.dispatcher.initial-delay-ms:1000}"
    )
    public void dispatchNext() {
        if (!accepting.get() || !startupRecovery.isComplete()) {
            return;
        }
        try {
            Optional<ClusteringRunSnapshot> claimed = lifecycleService.claimNextPending();
            claimed.ifPresent(this::submitClaimedRun);
        } catch (RuntimeException exception) {
            // A failed tick is isolated; the fixed-delay scheduler will poll again.
        }
    }

    private void submitClaimedRun(ClusteringRunSnapshot claimed) {
        try {
            taskExecutor.execute(() -> executeClaimedRun(claimed.runId()));
        } catch (RejectedExecutionException exception) {
            recordFailure(claimed.runId(), ClusteringRunFailureCode.DISPATCH_REJECTED);
        }
    }

    private void executeClaimedRun(String runId) {
        try {
            runExecutor.executeClaimedRun(runId);
        } catch (RuntimeException exception) {
            recordFailure(runId, ClusteringRunFailureCode.INTERNAL_ERROR);
        }
    }

    private void recordFailure(String runId, ClusteringRunFailureCode code) {
        try {
            failureService.markFailed(runId, code);
        } catch (RuntimeException exception) {
            // Failure recording is best effort here and must not terminate scheduling.
        }
    }

    @PreDestroy
    public void stopDispatching() {
        accepting.set(false);
    }
}
