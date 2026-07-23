package com.example.demo.community.service;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "community-clustering.python", name = "enabled", havingValue = "true")
public class CommunityClusteringStartupRecovery implements SmartInitializingSingleton {

    private final ClusteringRunLifecycleService lifecycleService;
    private final AtomicBoolean complete = new AtomicBoolean();

    public CommunityClusteringStartupRecovery(ClusteringRunLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        lifecycleService.recoverInterruptedRuns();
        complete.set(true);
    }

    public boolean isComplete() {
        return complete.get();
    }
}
