package com.example.campusactivity.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(ClusteringExecutionProperties.class)
@ConditionalOnProperty(
        prefix = "community-clustering.python",
        name = "enabled",
        havingValue = "true"
)
public class CommunityClusteringBackgroundConfig {
    public static final String EXECUTOR_BEAN = "communityClusteringTaskExecutor";

    @Bean(name = EXECUTOR_BEAN)
    ThreadPoolTaskExecutor communityClusteringTaskExecutor(
            ClusteringExecutionProperties properties
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("community-clustering-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds((int) properties.shutdownWait().getSeconds());
        return executor;
    }
}
