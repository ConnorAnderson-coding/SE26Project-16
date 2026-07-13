package com.example.demo.analytics.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalyticsEngineTest {

    @Test
    void sanitizesPersonalIdentifiersInFeedbackText() {
        String raw = "联系邮箱 student@example.com，手机 13812345678，学号 524030910001。";

        String sanitized = AnalyticsEngine.sanitizeFeedback(raw);

        assertEquals("联系邮箱 [邮箱已脱敏]，手机 [手机号已脱敏]，学号 [编号已脱敏]。", sanitized);
    }
}
