package com.example.demo.service;

import com.example.demo.config.AnalyticsConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmClientTest {

    private MockWebServer server;
    private AnalyticsConfig config;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        config = new AnalyticsConfig();
        config.setApiUrl(server.url("/v1/chat/completions").toString());
        config.setApiKey("test-key-123");
        config.setModel("deepseek-chat");
        config.setTimeout(Duration.ofSeconds(5));
        config.setMaxRetries(1);
        config.setMaxTokens(500);
        config.setTemperature(0.3);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private LlmClient client() {
        return new LlmClient(config, RestClient.builder());
    }

    private static String chatResponse(String contentJson) {
        // Escape for embedding into OpenAI-style envelope
        String escaped = contentJson
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return """
                {
                  "id": "chatcmpl-1",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "%s"
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """.formatted(escaped);
    }

    private static final String VALID_ARRAY = """
            [
              {"id":"id-1","category":"promotion","priority":"high","content":"加强宣传覆盖学院群与社团渠道以提升报名转化"},
              {"id":"id-2","category":"schedule","priority":"medium","content":"优化时间安排并在活动前发送提醒降低缺席"},
              {"id":"id-3","category":"venue","priority":"low","content":"提前检查场地设备并补充路线说明"},
              {"id":"id-4","category":"content","priority":"medium","content":"增加互动环节提升参与者获得感"}
            ]
            """;

    @Test
    void successfulJsonIsParsedAndValidated() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(chatResponse(VALID_ARRAY))
                .addHeader("Content-Type", "application/json"));

        List<Map<String, Object>> result = client().generateImprovements("活动摘要");

        assertThat(result).hasSize(4);
        assertThat(result.get(0).get("category")).isEqualTo("promotion");
        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-key-123");
        assertThat(req.getPath()).contains("/v1/chat/completions");
        assertThat(req.getBody().readUtf8()).contains("活动摘要");
    }

    @Test
    void stripsMarkdownFenceBeforeParsing() {
        String fenced = "```json\n" + VALID_ARRAY + "\n```";
        server.enqueue(new MockResponse()
                .setBody(chatResponse(fenced))
                .addHeader("Content-Type", "application/json"));

        List<Map<String, Object>> result = client().generateImprovements("摘要");
        assertThat(result).hasSize(4);
    }

    @Test
    void blankApiKeyFailsFastWithoutHttp() {
        config.setApiKey("");
        assertThatThrownBy(() -> client().generateImprovements("x"))
                .isInstanceOf(LlmClient.LlmCallException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void nonJsonContentTriggersNonRetryableFailure() {
        server.enqueue(new MockResponse()
                .setBody(chatResponse("这不是JSON，只是一段说明文字"))
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client().generateImprovements("摘要"))
                .isInstanceOf(LlmClient.LlmCallException.class)
                .satisfies(ex -> assertThat(((LlmClient.LlmCallException) ex).isRetryable()).isFalse());
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void missingRequiredCategoryFailsValidation() {
        String incomplete = """
                [
                  {"id":"id-1","category":"promotion","priority":"high","content":"加强宣传覆盖学院群与社团渠道以提升报名转化"},
                  {"id":"id-2","category":"schedule","priority":"medium","content":"优化时间安排并在活动前发送提醒降低缺席"},
                  {"id":"id-3","category":"venue","priority":"low","content":"提前检查场地设备并补充路线说明"}
                ]
                """;
        server.enqueue(new MockResponse()
                .setBody(chatResponse(incomplete))
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client().generateImprovements("摘要"))
                .isInstanceOf(LlmClient.LlmCallException.class)
                .hasMessageContaining("promotion, schedule, venue and content");
    }

    @Test
    void blankContentFieldFailsValidation() {
        String bad = """
                [
                  {"id":"id-1","category":"promotion","priority":"high","content":""},
                  {"id":"id-2","category":"schedule","priority":"medium","content":"优化时间安排并在活动前发送提醒降低缺席"},
                  {"id":"id-3","category":"venue","priority":"low","content":"提前检查场地设备并补充路线说明"},
                  {"id":"id-4","category":"content","priority":"medium","content":"增加互动环节提升参与者获得感"}
                ]
                """;
        server.enqueue(new MockResponse()
                .setBody(chatResponse(bad))
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client().generateImprovements("摘要"))
                .isInstanceOf(LlmClient.LlmCallException.class)
                .hasMessageContaining("blank id/content");
    }

    @Test
    void emptyAssistantContentFails() {
        server.enqueue(new MockResponse()
                .setBody(chatResponse("   "))
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client().generateImprovements("摘要"))
                .isInstanceOf(LlmClient.LlmCallException.class)
                .hasMessageContaining("为空");
    }

    @Test
    void http401IsNonRetryable() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));

        assertThatThrownBy(() -> client().generateImprovements("摘要"))
                .isInstanceOf(LlmClient.LlmCallException.class)
                .hasMessageContaining("4xx");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void http500RetriesThenSucceeds() throws Exception {
        config.setMaxRetries(1);
        server.enqueue(new MockResponse().setResponseCode(500).setBody("err"));
        server.enqueue(new MockResponse()
                .setBody(chatResponse(VALID_ARRAY))
                .addHeader("Content-Type", "application/json"));

        List<Map<String, Object>> result = client().generateImprovements("摘要");
        assertThat(result).hasSize(4);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void http500ExhaustsRetries() {
        config.setMaxRetries(1);
        server.enqueue(new MockResponse().setResponseCode(500).setBody("err1"));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("err2"));

        assertThatThrownBy(() -> client().generateImprovements("摘要"))
                .isInstanceOf(LlmClient.LlmCallException.class)
                .hasMessageContaining("已重试");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void getModelReturnsConfiguredModel() {
        assertThat(client().getModel()).isEqualTo("deepseek-chat");
    }
}
