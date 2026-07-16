package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
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

    @PostConstruct
    void logConfig() {
        log.info("[LLM 配置] apiUrl={}, apiKey长度={}, model={}",
                apiUrl,
                apiKey == null ? 0 : apiKey.length(),
                model);
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[LLM 配置] ⚠️ DEEPSEEK_API_KEY 未配置，改进建议将回退到规则模板");
        }
    }
}
