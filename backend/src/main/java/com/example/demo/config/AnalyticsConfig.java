package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.analytics.llm")
public class AnalyticsConfig {

    private String apiUrl = "https://api.deepseek.com/v1/chat/completions";

    private String apiKey = "";

    private String model = "deepseek-chat";

    private Duration timeout = Duration.ofSeconds(30);

    private int maxRetries = 3;

    private int maxTokens = 2000;

    private double temperature = 0.7;
}
