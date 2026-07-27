package com.example.demo.community.service;

import com.example.demo.config.CommunityClusteringBackgroundConfig;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "community-clustering.python", name = "enabled", havingValue = "true")
public class CommunityClusteringDispatcher {

    private final ClusteringRunLifecycleService lifecycleService;
    private final CommunityClusteringWorker worker;
    private final CommunityClusteringStartupRecovery startupRecovery;
    private final ThreadPoolTaskExecutor executor;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public CommunityClusteringDispatcher(
            ClusteringRunLifecycleService lifecycleService,
            CommunityClusteringWorker worker,
            CommunityClusteringStartupRecovery startupRecovery,
            @Qualifier(CommunityClusteringBackgroundConfig.EXECUTOR_BEAN) ThreadPoolTaskExecutor executor
    ) {
        this.lifecycleService = lifecycleService;
        this.worker = worker;
        this.startupRecovery = startupRecovery;
        this.executor = executor;
    }

    @Scheduled(
            fixedDelayString = "${community-clustering.dispatcher.poll-interval-ms:1000}",
            initialDelayString = "${community-clustering.dispatcher.initial-delay-ms:1000}"
    )
    public void dispatchNext() {
        if (!accepting.get() || !startupRecovery.isComplete()) {
            return;
        }
        lifecycleService.claimNextPending().ifPresent(this::submit);
    }

    private void submit(String runId) {
        try {
            executor.execute(() -> worker.execute(runId));
        } catch (RejectedExecutionException exception) {
            lifecycleService.markFailed(runId, "DISPATCH_REJECTED", "聚类执行队列已满");
        }
    }

    @PreDestroy
    public void stopDispatching() {
        accepting.set(false);
    }
}
