package com.example.demo.service;

import com.example.demo.config.AnalyticsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * 锁定 {@link LlmClient} 的重试分类：
 * <ul>
 *   <li>4xx（除 429）：不重试，立即抛出</li>
 *   <li>5xx / 429：可重试</li>
 *   <li>网络异常：可重试</li>
 *   <li>JSON 解析失败：不重试</li>
 *   <li>字段校验失败：不重试</li>
 *   <li>返回内容为空：不重试</li>
 *   <li>API key 缺失：不重试</li>
 * </ul>
 */
class LlmClientRetryClassificationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fourHundredOneDoesNotRetry() {
        TestEnv env = newEnv("test-key");
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"invalid api key\"}"));
        // 不应该再发第二次请求

        LlmClient.LlmCallException ex = assertThrows(
                LlmClient.LlmCallException.class,
                () -> env.client.generateImprovements("summary"));

        assertFalse(ex.isRetryable(), "401 必须不可重试");
        env.server.verify();
    }

    @Test
    void fourHundredDoesNotRetry() {
        TestEnv env = newEnv("test-key");
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"bad payload\"}"));

        LlmClient.LlmCallException ex = assertThrows(
                LlmClient.LlmCallException.class,
                () -> env.client.generateImprovements("summary"));

        assertFalse(ex.isRetryable(), "400 必须不可重试");
        env.server.verify();
    }

    @Test
    void fourTwentyNineIsRetryable() {
        TestEnv env = newEnvWithRetries("test-key", 2);
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        LlmClient.LlmCallException ex = assertThrows(
                LlmClient.LlmCallException.class,
                () -> env.client.generateImprovements("summary"));

        assertTrue(ex.isRetryable(), "耗尽重试后应当仍是 retryable=true，便于上层决定是否放弃");
        env.server.verify();
    }

    @Test
    void fiveHundredIsRetryable() {
        TestEnv env = newEnvWithRetries("test-key", 2);
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        LlmClient.LlmCallException ex = assertThrows(
                LlmClient.LlmCallException.class,
                () -> env.client.generateImprovements("summary"));

        assertTrue(ex.isRetryable(), "5xx 走完重试仍属可重试，便于上层落库为 failed");
        env.server.verify();
    }

    @Test
    void emptyContentIsNonRetryable() {
        TestEnv env = newEnv("test-key");
        // 返回 choices 为空数组
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));

        LlmClient.LlmCallException ex = assertThrows(
                LlmClient.LlmCallException.class,
                () -> env.client.generateImprovements("summary"));

        assertFalse(ex.isRetryable(), "空内容不可重试");
        env.server.verify();
    }

    @Test
    void invalidJsonIsNonRetryable() {
        TestEnv env = newEnv("test-key");
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"choices\": [{\"message\": {\"content\": \"not-json-at-all\"}}]}",
                        MediaType.APPLICATION_JSON));

        LlmClient.LlmCallException ex = assertThrows(
                LlmClient.LlmCallException.class,
                () -> env.client.generateImprovements("summary"));

        assertFalse(ex.isRetryable(), "JSON 解析失败不可重试");
        env.server.verify();
    }

    @Test
    void validationFailureIsNonRetryable() {
        TestEnv env = newEnv("test-key");
        // 内容有效 JSON，但只有 1 条（要求 3-5 条）
        String body = "{\"choices\": [{\"message\": {\"content\": \"[{\\\"id\\\":\\\"x\\\",\\\"category\\\":\\\"promotion\\\",\\\"priority\\\":\\\"high\\\",\\\"content\\\":\\\"y\\\"}]\"}}]}";
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        LlmClient.LlmCallException ex = assertThrows(
                LlmClient.LlmCallException.class,
                () -> env.client.generateImprovements("summary"));

        assertFalse(ex.isRetryable(), "字段校验失败不可重试");
        env.server.verify();
    }

    @Test
    void missingApiKeyIsNonRetryable() {
        LlmClient client = new LlmClient(config(""));
        LlmClient.LlmCallException ex = assertThrows(
                LlmClient.LlmCallException.class,
                () -> client.generateImprovements("summary"));

        assertFalse(ex.isRetryable(), "API key 缺失不可重试");
    }

    @Test
    void successfulResponseReturnsParsedSuggestions() throws Exception {
        TestEnv env = newEnv("test-key");
        String suggestionsJson = "["
                + "{\"id\":\"a\",\"category\":\"promotion\",\"priority\":\"high\",\"content\":\"推广\"},"
                + "{\"id\":\"b\",\"category\":\"schedule\",\"priority\":\"medium\",\"content\":\"时间\"},"
                + "{\"id\":\"c\",\"category\":\"venue\",\"priority\":\"high\",\"content\":\"场地\"},"
                + "{\"id\":\"d\",\"category\":\"content\",\"priority\":\"low\",\"content\":\"内容\"}"
                + "]";
        String body = "{\"choices\": [{\"message\": {\"content\": " + objectMapper.writeValueAsString(suggestionsJson) + "}}]}";

        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<java.util.Map<String, Object>> result = env.client.generateImprovements("summary");
        assertEquals(4, result.size());
        assertEquals("promotion", result.get(0).get("category"));
        env.server.verify();
    }

    @Test
    void networkExceptionIsRetryable() {
        TestEnv env = newEnvWithRetries("test-key", 1);
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withException(new java.net.SocketTimeoutException("read timeout")));
        env.server.expect(requestTo(env.cfg.getApiUrl()))
                .andExpect(method(POST))
                .andRespond(withException(new java.net.SocketTimeoutException("read timeout")));

        LlmClient.LlmCallException ex = assertThrows(
                LlmClient.LlmCallException.class,
                () -> env.client.generateImprovements("summary"));

        assertTrue(ex.isRetryable(), "网络异常属于可重试");
        env.server.verify();
    }

    /* ==================== helpers ==================== */

    /**
     * 用 {@code MockRestServiceServer.bindTo(RestClient.Builder)} 把底层 HTTP 请求拦下来，
     * 让断言可以严格控制每次请求的响应（包含重试次数、状态码、空内容等）。
     */
    private record TestEnv(AnalyticsConfig cfg, LlmClient client, MockRestServiceServer server) {}

    private static TestEnv newEnv(String apiKey) {
        return newEnvWithRetries(apiKey, 0);
    }

    private static TestEnv newEnvWithRetries(String apiKey, int retries) {
        AnalyticsConfig cfg = configWithRetries(apiKey, retries);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LlmClient client = new LlmClient(cfg, builder);
        return new TestEnv(cfg, client, server);
    }

    private static AnalyticsConfig config(String apiKey) {
        return configWithRetries(apiKey, 0);
    }

    private static AnalyticsConfig configWithRetries(String apiKey, int retries) {
        AnalyticsConfig cfg = new AnalyticsConfig();
        cfg.setApiKey(apiKey);
        cfg.setModel("deepseek-chat");
        cfg.setTimeout(Duration.ofSeconds(2));
        cfg.setMaxRetries(retries);
        cfg.setApiUrl("https://api.deepseek.test/v1/chat/completions");
        cfg.setMaxTokens(100);
        cfg.setTemperature(0.0);
        return cfg;
    }
}