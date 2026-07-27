package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClusteringClockConfig {

    @Bean
    Clock clusteringClock() {
        return Clock.systemDefaultZone();
    }
}
