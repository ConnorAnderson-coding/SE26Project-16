package com.example.demo.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

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
