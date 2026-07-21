package com.example.campusactivity.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityClusteringBackgroundConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CommunityClusteringBackgroundConfig.class);

    @Test
    void disabledPropertyCreatesNoWorkerExecutor() {
        contextRunner
                .withPropertyValues("community-clustering.python.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(
                        CommunityClusteringBackgroundConfig.EXECUTOR_BEAN
                ));
    }

    @Test
    void enabledExecutorIsSingleThreadBoundedAndExplicitlyConfigured() {
        contextRunner
                .withPropertyValues(
                        "community-clustering.python.enabled=true",
                        "community-clustering.execution.queue-capacity=3",
                        "community-clustering.execution.shutdown-wait=7s"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ThreadPoolTaskExecutor executor = context.getBean(
                            CommunityClusteringBackgroundConfig.EXECUTOR_BEAN,
                            ThreadPoolTaskExecutor.class
                    );
                    assertThat(executor.getCorePoolSize()).isEqualTo(1);
                    assertThat(executor.getMaxPoolSize()).isEqualTo(1);
                    assertThat(executor.getThreadNamePrefix())
                            .isEqualTo("community-clustering-worker-");
                    assertThat(executor.getThreadPoolExecutor().getQueue()
                            .remainingCapacity()).isEqualTo(3);
                    assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                            .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
                    assertThat(context.getBean(ClusteringExecutionProperties.class)
                            .shutdownWait()).isEqualTo(java.time.Duration.ofSeconds(7));
                });
    }
}
