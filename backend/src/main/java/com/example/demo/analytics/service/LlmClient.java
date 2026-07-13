package com.example.demo.analytics.service;

import com.example.demo.analytics.config.AnalyticsConfig;
import com.example.demo.analytics.dto.LlmRequest;
import com.example.demo.analytics.dto.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM API 调用客户端
 * <p>
 * 通过 HTTP 调用 DeepSeek Chat API，支持超时控制、失败重试、指数退避。
 */
@Slf4j
@Service
public class LlmClient {

    private final AnalyticsConfig config;
    private final RestClient restClient;

    private static final Set<String> REQUIRED_CATEGORIES = Set.of("promotion", "schedule", "venue", "content");
    private static final Set<String> ALLOWED_CATEGORIES = Set.of("promotion", "schedule", "venue", "content", "other");
    private static final Set<String> ALLOWED_PRIORITIES = Set.of("high", "medium", "low");

    private static final String STRICT_SYSTEM_PROMPT = """
            你是一个校园活动策划分析助手。请根据活动数据摘要、指标和用户评价，生成 4-5 条具体可执行的改进建议。

            要求：
            1. 建议列表必须覆盖四个维度：活动宣传 promotion、时间安排 schedule、场地设施 venue、内容质量 content。
            2. 每条建议必须包含 id、category、priority、content 四个字段。
            3. category 只能是 promotion/schedule/venue/content/other；priority 只能是 high/medium/low。
            4. content 使用中文，40-120 字，必须结合数据或用户反馈，避免空泛套话。
            5. 用户评价中出现的具体问题（如投影不清、排队太久、时间冲突、内容偏浅）必须体现在对应建议中。
            6. 只输出严格 JSON 数组，不要输出任何额外文本，不要使用 markdown 代码块。

            示例：
            [{"id":"id-1","category":"venue","priority":"high","content":"多名参与者反馈投影不清、后排体验差，建议下次改用大屏教室，提前完成投影、音响和座位视线检查。"}]
            """;

    public LlmClient(AnalyticsConfig config) {
        this.config = config;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(config.getTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 调用 LLM 生成改进建议
     *
     * @param dataSummary 活动数据摘要文本
     * @return 解析后的建议列表（每条为 Map，含 id/category/priority/content）
     * @throws LlmCallException 调用失败或解析失败时抛出
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> generateImprovements(String dataSummary) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new LlmCallException("未配置 DEEPSEEK_API_KEY，使用规则模板降级");
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
                                .content("数据摘要：\n" + dataSummary)
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
                    log.info("LLM 调用重试 第{}次, 等待{}ms", attempt, backoffMs);
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
                    throw new LlmCallException("LLM 返回空内容");
                }

                // 清理可能的 markdown 代码块包裹
                content = cleanJsonContent(content);

                // 用 Jackson 解析建议 JSON 数组
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> suggestions = mapper.readValue(content, List.class);
                validateSuggestions(suggestions);
                log.info("LLM 生成 {} 条建议", suggestions.size());
                return suggestions;

            } catch (LlmCallException e) {
                lastException = e;
                log.warn("LLM 调用失败 (attempt {}/{}): {}",
                        attempt + 1, config.getMaxRetries() + 1, e.getMessage());
            } catch (Exception e) {
                lastException = e;
                log.warn("LLM 调用异常 (attempt {}/{}): {}",
                        attempt + 1, config.getMaxRetries() + 1, e.getMessage());
            }
        }

        throw new LlmCallException(
                "LLM 调用失败，已重试 " + config.getMaxRetries() + " 次", lastException);
    }

    /**
     * 清理 LLM 返回内容中可能含有的 markdown 代码块标记
     */
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
            throw new LlmCallException("LLM suggestions must contain 3 to 5 items");
        }

        Set<String> categories = new HashSet<>();
        for (int i = 0; i < suggestions.size(); i++) {
            Map<String, Object> item = suggestions.get(i);
            String id = asText(item.get("id"));
            String category = asText(item.get("category"));
            String priority = asText(item.get("priority"));
            String content = asText(item.get("content"));

            if (id.isBlank() || content.isBlank()) {
                throw new LlmCallException("LLM suggestion item has blank id/content at index " + i);
            }
            if (!ALLOWED_CATEGORIES.contains(category)) {
                throw new LlmCallException("LLM suggestion category is invalid: " + category);
            }
            if (!ALLOWED_PRIORITIES.contains(priority)) {
                throw new LlmCallException("LLM suggestion priority is invalid: " + priority);
            }
            categories.add(category);
        }

        if (!categories.containsAll(REQUIRED_CATEGORIES)) {
            throw new LlmCallException("LLM suggestions must cover promotion, schedule, venue and content");
        }
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * LLM 调用异常
     */
    public static class LlmCallException extends RuntimeException {
        public LlmCallException(String message) {
            super(message);
        }

        public LlmCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
