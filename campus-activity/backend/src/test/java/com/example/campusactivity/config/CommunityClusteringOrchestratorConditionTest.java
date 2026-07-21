package com.example.campusactivity.config;

import com.example.campusactivity.service.clustering.CommunityClusteringRunExecutor;
import com.example.campusactivity.service.clustering.CommunityClusteringOrchestrator;
import com.example.campusactivity.service.clustering.CommunityClusteringSubmissionService;
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
    void enabledPropertyCreatesExactlyOneOrchestratorWithSharedServices() {
        contextRunner
                .withPropertyValues("community-clustering.python.enabled=true")
                .withBean(
                        CommunityClusteringSubmissionService.class,
                        () -> mock(CommunityClusteringSubmissionService.class)
                )
                .withBean(
                        CommunityClusteringRunExecutor.class,
                        () -> mock(CommunityClusteringRunExecutor.class)
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
