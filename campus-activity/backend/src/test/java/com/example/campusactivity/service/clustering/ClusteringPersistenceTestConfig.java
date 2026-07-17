package com.example.campusactivity.service.clustering;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@TestConfiguration(proxyBeanMethods = false)
class ClusteringPersistenceTestConfig {
    static final Instant FIXED_INSTANT = Instant.parse("2026-07-16T08:00:00Z");

    @Bean
    @Primary
    Clock fixedClusteringClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }
}
