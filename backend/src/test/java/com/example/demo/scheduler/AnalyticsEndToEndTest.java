package com.example.demo.scheduler;

import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.dto.analytics.SuggestionItem;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityAnalysis;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.ActivityRepository;
import com.example.demo.service.SuggestionGenerator;
import com.example.demo.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LLM 建议生成 + 定时调度分析 的端到端验证。
 *
 * 目标：在未配置 DEEPSEEK_API_KEY 时，验证
 *   1) SuggestionGenerator.fallbackSafe() 能产出 4 类覆盖的规则建议
 *   2) POST /api/v1/analytics/activity/{id}/generate 触发异步分析后，
 *      LLM 调用被快速降级，规则模板最终落库
 *   3) AnalysisScheduler.refreshEndedActivitiesAnalysis() 对昨日结束活动
 *      成功派发分析任务，DB 中 analysis_status 达到 ready
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // 开启 AnalyticsController 手动触发端点
        "app.analytics.manual-trigger=true",
        // 显式清空 API Key，确保 LLM 调用快速降级到规则模板
        "app.analytics.llm.api-key="
})
class AnalyticsEndToEndTest extends IntegrationTestSupport {

    @Autowired
    private SuggestionGenerator suggestionGenerator;

    @Autowired
    private AnalysisScheduler analysisScheduler;

    @Autowired
    private ActivityAnalysisRepository analysisRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private TestScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = createScenario();
        // 默认 saveActivity 的 endTime 在未来；调整为昨天中午，让调度器能命中
        transactionTemplate.executeWithoutResult(status -> {
            Activity a = activityRepository.findById(scenario.activity().getId()).orElseThrow();
            LocalDateTime yesterdayNoon = LocalDateTime.now()
                    .minusDays(1).toLocalDate().atTime(12, 0);
            a.setStartTime(yesterdayNoon.minusHours(2));
            a.setEndTime(yesterdayNoon);
            a.setStatus("ended");
            activityRepository.save(a);
        });
    }

    // ---------------------------------------------------------------------
    // 1) 规则模板：纯逻辑路径，不依赖 Spring / LLM / 调度
    // ---------------------------------------------------------------------
    @Test
    void fallbackSafeGeneratesAllRequiredCategories() {
        ActivityMetrics metrics = ActivityMetrics.builder()
                .signupRate(new BigDecimal("20.0"))   // < 50 → promotion 高优
                .attendanceRate(new BigDecimal("40.0")) // < 70 → schedule 高优
                .avgRating(new BigDecimal("3.8"))
                .feedbackContents(List.of("教室音响太差"))
                .build();

        List<SuggestionItem> suggestions = suggestionGenerator.fallbackSafe(metrics);

        assertThat(suggestions)
                .as("规则模板至少应产生 4 条建议，覆盖 promotion/schedule/venue/content 四类")
                .hasSizeGreaterThanOrEqualTo(4)
                .extracting(SuggestionItem::getCategory)
                .contains("promotion", "schedule", "venue");
        assertThat(suggestions)
                .extracting(SuggestionItem::getPriority)
                .contains("high");
    }

    // ---------------------------------------------------------------------
    // 2) HTTP 触发端点 → 异步执行 → 规则模板落库
    // ---------------------------------------------------------------------
    @Test
    void triggerGenerateEndpointFallsBackToRulesWhenLlmKeyMissing() throws Exception {
        Long activityId = scenario.activity().getId();

        // 异步立即返回 200
        authPost(scenario.organizerToken(),
                        "/api/v1/analytics/activity/" + activityId + "/generate", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitted").value(true));

        // 等待异步分析在 llmExecutor 线程池上完成
        await().atMost(15, SECONDS).pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ActivityAnalysis saved = analysisRepository.findByActivityId(activityId).orElse(null);
                    assertThat(saved)
                            .as("异步分析应最终写入 ActivityAnalysis 行")
                            .isNotNull();
                    assertThat(saved.getAnalysisStatus())
                            .as("无 API Key 时最终应处于 ready（由规则模板兜底）")
                            .isEqualTo("ready");
                    assertThat(saved.getSuggestionSource())
                            .as("来源必须如实标记为 rule，不能伪装成 llm")
                            .isEqualTo("rule");
                    assertThat(saved.getSuggestions())
                            .as("规则模板应产出非空建议列表")
                            .isNotEmpty();
                });
    }

    // ---------------------------------------------------------------------
    // 3) 定时调度：对昨日结束活动派发分析任务，最终落库 ready
    // ---------------------------------------------------------------------
    @Test
    void schedulerDispatchesAnalysisForYesterdayEndedActivity() {
        Long activityId = scenario.activity().getId();

        // 直接同步执行调度方法（与每日 02:00 cron 等价）
        analysisScheduler.refreshEndedActivitiesAnalysis();

        await().atMost(15, SECONDS).pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ActivityAnalysis saved = analysisRepository.findByActivityId(activityId).orElse(null);
                    assertThat(saved)
                            .as("调度器对昨日结束活动派发的任务应最终写入 DB")
                            .isNotNull();
                    assertThat(saved.getAnalysisStatus()).isEqualTo("ready");
                    assertThat(saved.getSuggestionSource()).isEqualTo("rule");
                    assertThat(saved.getSuggestions()).isNotEmpty();
                    // 规则模板建议至少 4 条；map 形态以 List<Map> 存储
                    assertThat((List<?>) saved.getSuggestions().stream().toList())
                            .hasSizeGreaterThanOrEqualTo(4);
                });
    }

    // ---------------------------------------------------------------------
    // 4) 调度器幂等：第二次执行不应再产生新分析行（已有 LLM 固化建议则跳过）
    //    本用例的兜底结果是 "rule"，属于"待升级"状态，再次执行应升级派发。
    // ---------------------------------------------------------------------
    @Test
    void schedulerIdempotencyKeepsExistingRuleTemplateOnUpgrade() {
        Long activityId = scenario.activity().getId();
        analysisScheduler.refreshEndedActivitiesAnalysis();

        await().atMost(15, SECONDS).pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(analysisRepository.findByActivityId(activityId))
                        .isPresent());

        // 截一份快照（计数建议数）
        ActivityAnalysis first = analysisRepository.findByActivityId(activityId).orElseThrow();
        Object firstSuggestions = first.getSuggestions();
        Integer firstCount = firstSuggestions == null ? 0 : ((List<?>) firstSuggestions).size();

        // 再次执行调度：应进入"规则升级"分支，若 LLM 仍失败则保留原 rule 记录
        analysisScheduler.refreshEndedActivitiesAnalysis();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        ActivityAnalysis second = analysisRepository.findByActivityId(activityId).orElseThrow();
        // 升级路径在 LLM 失败时会保留原规则记录，suggestionSource 仍为 rule
        assertThat(second.getSuggestionSource()).isEqualTo("rule");
        assertThat(second.getAnalysisStatus()).isEqualTo("ready");
        Object secondSuggestions = second.getSuggestions();
        Integer secondCount = secondSuggestions == null ? 0 : ((List<?>) secondSuggestions).size();
        assertThat(secondCount)
                .as("重复调度不应改变已 ready 的建议条数")
                .isEqualTo(firstCount);
        // 顺手验证字段形状（每条建议至少应有 content 字段）
        if (secondSuggestions instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> first0) {
            assertThat(first0.containsKey("content"))
                    .as("规则模板落库后每条建议都应有 content 字段")
                    .isTrue();
        }
    }
}