package com.example.demo.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 分析模块配置属性
 * <p>
 * 对应 application.properties 中 {@code app.analytics} 前缀的配置项。
 * 支持通过环境变量覆盖：{@code ANALYTICS_LLM_API_KEY} 等。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.analytics.llm")
public class AnalyticsConfig {

    /** LLM API 地址，如 https://api.deepseek.com/v1/chat/completions */
    private String apiUrl = "https://api.deepseek.com/v1/chat/completions";

    /** API Key（通过环境变量 DEEPSEEK_API_KEY 注入） */
    private String apiKey = "";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 请求超时时间 */
    private Duration timeout = Duration.ofSeconds(30);

    /** 最大重试次数 */
    private int maxRetries = 3;

    /** 最大生成 token 数 */
    private int maxTokens = 2000;

    /** 生成温度 (0-2) */
    private double temperature = 0.7;
}
