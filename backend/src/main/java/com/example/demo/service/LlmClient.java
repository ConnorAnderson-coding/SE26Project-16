package com.example.demo.service;

import com.example.demo.config.AnalyticsConfig;
import com.example.demo.dto.analytics.LlmRequest;
import com.example.demo.dto.analytics.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class LlmClient {

    private final AnalyticsConfig config;
    private final RestClient restClient;

    private static final Set<String> REQUIRED_CATEGORIES = Set.of("promotion", "schedule", "venue", "content");
    private static final Set<String> ALLOWED_CATEGORIES = Set.of("promotion", "schedule", "venue", "content", "other");
    private static final Set<String> ALLOWED_PRIORITIES = Set.of("high", "medium", "low");

    private static final String STRICT_SYSTEM_PROMPT = """
            你是校园活动运营分析助手。请根据活动指标、报名与签到数据、评分分布和文字反馈，
            生成 3-5 条可执行的下一次活动改进建议。

            要求：
            1. 建议尽量覆盖 promotion、schedule、venue、content 四类。
            2. 只返回 JSON 数组，每项包含 id、category、priority、content。
            3. category 只能是 promotion/schedule/venue/content/other。
            4. priority 只能是 high/medium/low。
            5. content 使用中文，控制在 40-120 字，必须具体可执行。
            6. 不要返回 Markdown、解释文字或 JSON 以外的内容。

            示例：
            [{"id":"id-1","category":"venue","priority":"high","content":"提前检查场地投影、音响和网络，并在报名确认消息中补充到场路线，降低现场等待和迟到概率。"}]
            """;

    @Autowired
    public LlmClient(AnalyticsConfig config) {
        this(config, defaultBuilder(config));
    }

    /**
     * 允许注入自定义 {@link RestClient.Builder}，便于单元测试用
     * {@code MockRestServiceServer.bindTo(builder)} 接管底层 HTTP。
     */
    LlmClient(AnalyticsConfig config, RestClient.Builder builder) {
        this.config = config;
        this.restClient = builder.build();
    }

    private static RestClient.Builder defaultBuilder(AnalyticsConfig config) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(config.getTimeout());
        return RestClient.builder().requestFactory(requestFactory);
    }

    /**
     * 暴露模型名称供审计使用（例如写入 {@code activity_analysis.suggestion_model}）。
     */
    public String getModel() {
        return config.getModel();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> generateImprovements(String dataSummary) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            // 配置缺失属于"环境问题"，重试无意义，直接抛出不可重试异常
            log.warn("LLM 未启用：app.analytics.llm.api-key 为空。请在 application.properties 或环境变量 DEEPSEEK_API_KEY 中配置。");
            throw new LlmCallException("未配置 DEEPSEEK_API_KEY，无法调用 LLM 服务", false);
        }

        LlmRequest request = LlmRequest.builder()
                .model(config.getModel())
                .messages(List.of(
                        LlmRequest.Message.builder()
                                .role("system")
                                .content(STRICT_SYSTEM_PROMPT)
                                .build(),
                        LlmRequest.Message.builder()
                                .role("user")
                                .content("请基于以下活动数据生成改进建议：\n" + dataSummary)
                                .build()
                ))
                .maxTokens(config.getMaxTokens())
                .temperature(config.getTemperature())
                .build();

        Exception lastException = null;
        for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
            try {
                if (attempt > 0) {
                    long backoffMs = (long) Math.pow(2, attempt) * 1000;
                    log.info("LLM 调用重试第 {} 次，等待 {}ms", attempt, backoffMs);
                    Thread.sleep(backoffMs);
                }

                LlmResponse response = restClient.post()
                        .uri(config.getApiUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .body(request)
                        .retrieve()
                        .body(LlmResponse.class);

                String content = response.getContent();

                if (content == null || content.isBlank()) {
                    // 内容为空是服务端 / 协议层问题，重试也无济于事，直接放弃
                    throw new LlmCallException("LLM 返回内容为空", false);
                }

                content = cleanJsonContent(content);

                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Map<String, Object>> suggestions = mapper.readValue(content, List.class);
                validateSuggestions(suggestions); // 校验失败属于不可重试
                log.info("LLM 生成 {} 条改进建议", suggestions.size());
                return suggestions;

            } catch (LlmCallException e) {
                lastException = e;
                // 不可重试异常：直接抛，不再进入下一轮
                if (!e.retryable) {
                    log.warn("LLM 调用不可重试错误：{}", e.getMessage());
                    throw e;
                }
                log.warn("LLM 调用失败 (attempt {}/{}): {}",
                        attempt + 1, config.getMaxRetries() + 1, e.getMessage());
            } catch (HttpClientErrorException e) {
                // 4xx：除 429 外都视为不可重试（认证失败、参数错误、payload 过大等）
                HttpStatusCode status = e.getStatusCode();
                lastException = e;
                boolean retryable = status.value() == 429;
                log.warn("LLM 调用 4xx (status={}): {}", status, e.getMessage());
                if (!retryable) {
                    throw new LlmCallException(
                            "LLM 调用 4xx 不可重试 (status=" + status + "): " + e.getMessage(), false, e);
                }
            } catch (HttpServerErrorException e) {
                // 5xx：服务端问题，可重试
                lastException = e;
                log.warn("LLM 调用 5xx (status={}): {}", e.getStatusCode(), e.getMessage());
            } catch (ResourceAccessException e) {
                // 网络/超时：可重试
                lastException = e;
                log.warn("LLM 调用网络异常 (attempt {}/{}): {}",
                        attempt + 1, config.getMaxRetries() + 1, e.getMessage());
            } catch (RestClientResponseException e) {
                // 其他 RestClient 响应异常：根据状态码分类
                lastException = e;
                if (e.getStatusCode().is4xxClientError() && e.getStatusCode().value() != 429) {
                    throw new LlmCallException(
                            "LLM 调用 4xx 不可重试 (status=" + e.getStatusCode() + "): " + e.getMessage(),
                            false, e);
                }
                log.warn("LLM 调用响应异常 (status={}): {}", e.getStatusCode(), e.getMessage());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // JSON 解析失败：内容确实非法，重试结果一致 → 不可重试
                lastException = e;
                log.warn("LLM 返回内容 JSON 解析失败：{}", e.getMessage());
                throw new LlmCallException("LLM 返回 JSON 解析失败: " + e.getMessage(), false, e);
            } catch (RestClientException e) {
                // 兜底：可能是超时或未知错误，标记为可重试
                lastException = e;
                log.warn("LLM 调用异常 (attempt {}/{}): {}",
                        attempt + 1, config.getMaxRetries() + 1, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmCallException("LLM 调用被中断", false, e);
            }
        }

        throw new LlmCallException(
                "LLM 调用失败，已重试 " + config.getMaxRetries() + " 次", true, lastException);
    }

    private String cleanJsonContent(String content) {
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }

    private void validateSuggestions(List<Map<String, Object>> suggestions) {
        if (suggestions == null || suggestions.size() < 3 || suggestions.size() > 5) {
            throw new LlmCallException("LLM suggestions must contain 3 to 5 items", false);
        }

        Set<String> categories = new HashSet<>();
        for (int i = 0; i < suggestions.size(); i++) {
            Map<String, Object> item = suggestions.get(i);
            String id = asText(item.get("id"));
            String category = asText(item.get("category"));
            String priority = asText(item.get("priority"));
            String content = asText(item.get("content"));

            if (id.isBlank() || content.isBlank()) {
                throw new LlmCallException(
                        "LLM suggestion item has blank id/content at index " + i, false);
            }
            if (!ALLOWED_CATEGORIES.contains(category)) {
                throw new LlmCallException("LLM suggestion category is invalid: " + category, false);
            }
            if (!ALLOWED_PRIORITIES.contains(priority)) {
                throw new LlmCallException("LLM suggestion priority is invalid: " + priority, false);
            }
            categories.add(category);
        }

        if (!categories.containsAll(REQUIRED_CATEGORIES)) {
            throw new LlmCallException(
                    "LLM suggestions must cover promotion, schedule, venue and content", false);
        }
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * LLM 调用异常，{@code retryable} 标识调用方是否应该重试。
     * <p>
     * - {@code retryable=false}：4xx（除 429）、JSON 解析失败、字段校验失败、内容为空——继续重试不会改变结果。
     * - {@code retryable=true}：5xx、网络超时、429——值得退避后再试。
     */
    public static class LlmCallException extends RuntimeException {
        private final boolean retryable;

        public LlmCallException(String message) {
            this(message, true);
        }

        public LlmCallException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public LlmCallException(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}