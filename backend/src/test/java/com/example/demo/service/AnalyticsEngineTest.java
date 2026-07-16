package com.example.demo.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsEngineTest {

    @Test
    void sanitizesPersonalIdentifiersInFeedbackText() {
        String raw = "Email student@example.com phone 13812345678 id 524030910001.";

        String sanitized = AnalyticsEngine.sanitizeFeedback(raw);

        assertFalse(sanitized.contains("student@example.com"));
        assertFalse(sanitized.contains("13812345678"));
        assertFalse(sanitized.contains("524030910001"));
        assertTrue(sanitized.contains("\u90ae\u7bb1\u5df2\u8131\u654f"));
        assertTrue(sanitized.contains("\u624b\u673a\u53f7\u5df2\u8131\u654f"));
        assertTrue(sanitized.contains("\u7f16\u53f7\u5df2\u8131\u654f"));
    }
}
