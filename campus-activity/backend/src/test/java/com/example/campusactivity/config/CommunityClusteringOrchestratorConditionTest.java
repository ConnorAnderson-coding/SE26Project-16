package com.example.campusactivity.config;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.service.clustering.ClusteringResponseValidator;
import com.example.campusactivity.service.clustering.ClusteringResultPersistenceService;
import com.example.campusactivity.service.clustering.ClusteringRunFailureService;
import com.example.campusactivity.service.clustering.ClusteringRunLifecycleService;
import com.example.campusactivity.service.clustering.CommunityClusteringOrchestrator;
import com.example.campusactivity.service.clustering.CommunityFeatureAggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CommunityClusteringOrchestratorConditionTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OrchestratorConfiguration.class);

    @Test
    void disabledPropertyStartsWithoutOrchestratorOrItsDependencies() {
        contextRunner
                .withPropertyValues("community-clustering.python.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CommunityClusteringOrchestrator.class);
                });
    }

    @Test
    void missingPropertyStartsWithoutOrchestratorOrItsDependencies() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CommunityClusteringOrchestrator.class);
        });
    }

    @Test
    void enabledPropertyCreatesExactlyOneOrchestratorWhenSixDependenciesExist() {
        contextRunner
                .withPropertyValues("community-clustering.python.enabled=true")
                .withBean(
                        CommunityFeatureAggregationService.class,
                        () -> mock(CommunityFeatureAggregationService.class)
                )
                .withBean(
                        ClusteringRunLifecycleService.class,
                        () -> mock(ClusteringRunLifecycleService.class)
                )
                .withBean(
                        ClusteringRunFailureService.class,
                        () -> mock(ClusteringRunFailureService.class)
                )
                .withBean(ClusteringClient.class, () -> mock(ClusteringClient.class))
                .withBean(
                        ClusteringResponseValidator.class,
                        () -> mock(ClusteringResponseValidator.class)
                )
                .withBean(
                        ClusteringResultPersistenceService.class,
                        () -> mock(ClusteringResultPersistenceService.class)
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CommunityClusteringOrchestrator.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CommunityClusteringOrchestrator.class)
    static class OrchestratorConfiguration {
    }
}
