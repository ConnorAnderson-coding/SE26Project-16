package com.example.campusactivity.service.clustering;

import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.repository.ClusteringRunRepository;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        prefix = "community-clustering.python",
        name = "enabled",
        havingValue = "true"
)
public class CommunityClusteringStartupRecovery implements SmartInitializingSingleton {
    private final ClusteringRunRepository runRepository;
    private final ClusteringRunFailureService failureService;
    private final AtomicBoolean complete = new AtomicBoolean();

    public CommunityClusteringStartupRecovery(
            ClusteringRunRepository runRepository,
            ClusteringRunFailureService failureService
    ) {
        this.runRepository = runRepository;
        this.failureService = failureService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        recover();
    }

    public synchronized void recover() {
        List<String> interruptedRunIds = runRepository
                .findIdsByStatusOrderByCreatedAtAscIdAsc(ClusteringRunStatus.RUNNING);
        for (String runId : interruptedRunIds) {
            FailureRecordingResult result = failureService.markFailed(
                    runId,
                    ClusteringRunFailureCode.EXECUTION_INTERRUPTED
            );
            if (result == null
                    || result.outcome() == FailureRecordingResult.Outcome.RUN_NOT_FOUND) {
                throw new IllegalStateException("Unable to recover interrupted clustering run");
            }
        }
        complete.set(true);
    }

    public boolean isComplete() {
        return complete.get();
    }
}
