package com.example.campusactivity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ClusteringClockConfig {
    @Bean
    Clock clusteringClock() {
        return Clock.systemUTC();
    }
}
