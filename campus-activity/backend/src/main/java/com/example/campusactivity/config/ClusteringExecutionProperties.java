package com.example.campusactivity.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "community-clustering.execution")
public record ClusteringExecutionProperties(
        @Min(1) Integer queueCapacity,
        @NotNull Duration shutdownWait
) {
    public ClusteringExecutionProperties {
        queueCapacity = queueCapacity == null ? 8 : queueCapacity;
        shutdownWait = shutdownWait == null ? Duration.ofSeconds(10) : shutdownWait;
        if (shutdownWait.isNegative()
                || shutdownWait.isZero()
                || shutdownWait.getSeconds() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("shutdownWait must be between 1 second and Integer.MAX_VALUE seconds");
        }
    }
}
