package com.example.demo.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DeepSeek API 请求体
 */
@Data
@Builder
public class LlmRequest {

    private String model;

    private List<Message> messages;

    @Builder.Default
    private int maxTokens = 2000;

    @Builder.Default
    private double temperature = 0.7;

    @Data
    @Builder
    public static class Message {
        private String role;
        private String content;
    }
}
