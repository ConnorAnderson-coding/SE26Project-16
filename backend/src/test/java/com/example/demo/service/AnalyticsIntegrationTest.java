package com.example.demo.service;

import com.example.demo.common.CacheNames;
import com.example.demo.dto.analytics.ActivityMetrics;
import com.example.demo.entity.Activity;
import com.example.demo.entity.ActivityAnalysis;
import com.example.demo.entity.Feedback;
import com.example.demo.entity.Registration;
import com.example.demo.entity.User;
import com.example.demo.repository.ActivityAnalysisRepository;
import com.example.demo.repository.FeedbackRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.scheduler.AnalysisScheduler;
import com.example.demo.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分析模块集成测试：H2 真表 + simple 缓存 + 无 LLM Key 时规则降级。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.analytics.manual-trigger=true",
        "app.analytics.llm.api-key=",
        "app.elasticsearch.enabled=false"
})
class AnalyticsIntegrationTest extends IntegrationTestSupport {

    @Autowired AnalyticsEngine analyticsEngine;
    @Autowired AnalysisScheduler analysisScheduler;
    @Autowired LlmAnalysisRunner llmAnalysisRunner;
    @Autowired ActivityAnalysisRepository analysisRepository;
    @Autowired RegistrationRepository registrationRepository;
    @Autowired FeedbackRepository feedbackRepository;
    @Autowired CacheManager cacheManager;

    private TestScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = createScenario();
        transactionTemplate.executeWithoutResult(status -> {
            Activity a = activityRepository.findById(scenario.activity().getId()).orElseThrow();
            a.setViewCount(100);
            a.setSignupCount(40);
            a.setFavoriteCount(8);
            a.setMaxParticipants(50);
            LocalDateTime yesterdayNoon = LocalDateTime.now().minusDays(1).toLocalDate().atTime(12, 0);
            a.setStartTime(yesterdayNoon.minusHours(2));
            a.setEndTime(yesterdayNoon);
            a.setStatus("ended");
            activityRepository.save(a);
        });
        // 清分析缓存，避免场景间污染
        Cache c = cacheManager.getCache(CacheNames.ANALYTICS_ACTIVITY);
        if (c != null) {
            c.clear();
        }
    }

    @Test
    void computeMetricsFromDatabaseWithRegistrationsAndFeedback() {
        Long activityId = scenario.activity().getId();
        seedRegistrationAndFeedback(activityId);

        ActivityMetrics metrics = analyticsEngine.computeMetrics(activityId);

        assertThat(metrics.getViewCount()).isEqualTo(100);
        assertThat(metrics.getSignupCount()).isEqualTo(40);
        assertThat(metrics.getSignupRate()).isEqualByComparingTo("40.0");
        assertThat(metrics.getApprovedCount()).isEqualTo(2L);
        assertThat(metrics.getFeedbackCount()).isEqualTo(2L);
        assertThat(metrics.getAvgRating()).isEqualByComparingTo("4.50");
        assertThat(metrics.getRatingDistribution())
                .containsEntry(5, 1L)
                .containsEntry(4, 1L);
        assertThat(metrics.getFeedbackContents())
                .anyMatch(s -> s.contains("[手机号已脱敏]"));
    }

    @Test
    void zeroSignupZeroFeedbackScenario() {
        Long activityId = scenario.activity().getId();
        transactionTemplate.executeWithoutResult(status -> {
            Activity a = activityRepository.findById(activityId).orElseThrow();
            a.setViewCount(20);
            a.setSignupCount(0);
            activityRepository.save(a);
        });
        Cache c = cacheManager.getCache(CacheNames.ANALYTICS_ACTIVITY);
        if (c != null) {
            c.evict(activityId);
        }

        ActivityMetrics metrics = analyticsEngine.computeMetrics(activityId);

        assertThat(metrics.getSignupCount()).isEqualTo(0);
        assertThat(metrics.getSignupRate()).isEqualByComparingTo("0.0");
        assertThat(metrics.getAttendanceRate()).isEqualByComparingTo("0.0");
        assertThat(metrics.getFeedbackCount()).isEqualTo(0L);
        assertThat(metrics.getAvgRating()).isNull();
    }

    @Test
    void analyticsCacheHitSkipsDbMutationUntilEvict() {
        Long activityId = scenario.activity().getId();
        ActivityMetrics first = analyticsEngine.computeMetrics(activityId);
        assertThat(first.getSignupCount()).isEqualTo(40);

        // 改库但不清缓存
        transactionTemplate.executeWithoutResult(status -> {
            Activity a = activityRepository.findById(activityId).orElseThrow();
            a.setSignupCount(99);
            activityRepository.save(a);
        });

        ActivityMetrics cached = analyticsEngine.computeMetrics(activityId);
        assertThat(cached.getSignupCount())
                .as("缓存命中时应仍返回旧指标")
                .isEqualTo(40);

        Cache c = cacheManager.getCache(CacheNames.ANALYTICS_ACTIVITY);
        assertThat(c).isNotNull();
        c.evict(activityId);

        ActivityMetrics fresh = analyticsEngine.computeMetrics(activityId);
        assertThat(fresh.getSignupCount())
                .as("失效后应读到新 signup_count")
                .isEqualTo(99);
    }

    @Test
    void fullPipelineMockLlmViaMissingKeyPersistsRuleSuggestions() {
        Long activityId = scenario.activity().getId();
        ActivityMetrics metrics = analyticsEngine.computeMetrics(activityId);

        // 同步调用 runner（测试环境直接调 bean 方法；@Async 对同类外调用仍走代理）
        llmAnalysisRunner.runAsync(activityId, metrics);

        await().atMost(15, SECONDS).pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ActivityAnalysis saved = analysisRepository.findByActivityId(activityId).orElse(null);
                    assertThat(saved).isNotNull();
                    assertThat(saved.getAnalysisStatus()).isEqualTo("ready");
                    assertThat(saved.getSuggestionSource()).isEqualTo("rule");
                    assertThat(saved.getSuggestions()).isNotEmpty();
                    assertThat(saved.getSignupRate()).isNotNull();
                    assertThat(saved.getAttendanceRate()).isNotNull();
                });
    }

    @Test
    void schedulerProcessesYesterdayEndedActivity() {
        Long activityId = scenario.activity().getId();
        analysisScheduler.refreshEndedActivitiesAnalysis();

        await().atMost(15, SECONDS).pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ActivityAnalysis saved = analysisRepository.findByActivityId(activityId).orElse(null);
                    assertThat(saved).isNotNull();
                    assertThat(saved.getAnalysisStatus()).isEqualTo("ready");
                    assertThat(saved.getSuggestions()).isNotEmpty();
                });
    }

    @Test
    void generateEndpointRequiresOrganizerAndPersists() throws Exception {
        Long activityId = scenario.activity().getId();

        authPost(scenario.studentToken(),
                "/api/v1/analytics/activity/" + activityId + "/generate", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        authPost(scenario.organizerToken(),
                "/api/v1/analytics/activity/" + activityId + "/generate", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitted").value(true));

        await().atMost(15, SECONDS).pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ActivityAnalysis saved = analysisRepository.findByActivityId(activityId).orElse(null);
                    assertThat(saved).isNotNull();
                    assertThat(saved.getSuggestionSource()).isEqualTo("rule");
                });

        // 幂等：已有 llm 才跳过；rule 可再触发。此处再触发应仍 ready
        authPost(scenario.organizerToken(),
                "/api/v1/analytics/activity/" + activityId + "/generate", null)
                .andExpect(status().isOk());
    }

    @Test
    void metricsEndpointReturnsOrganizerOnly() throws Exception {
        Long activityId = scenario.activity().getId();
        authGet(scenario.organizerToken(), "/api/v1/analytics/activity/" + activityId + "/metrics")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signupCount").value(40))
                .andExpect(jsonPath("$.data.viewCount").value(100));

        authGet(scenario.studentToken(), "/api/v1/analytics/activity/" + activityId + "/metrics")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));
    }

    private void seedRegistrationAndFeedback(Long activityId) {
        transactionTemplate.executeWithoutResult(status -> {
            Activity a = activityRepository.findById(activityId).orElseThrow();
            User u1 = scenario.student();
            User u2 = scenario.admin();

            Registration r1 = new Registration();
            r1.setActivity(a);
            r1.setUser(u1);
            r1.setStatus("approved");
            r1.setCreatedAt(LocalDateTime.now().minusDays(3));
            registrationRepository.save(r1);

            Registration r2 = new Registration();
            r2.setActivity(a);
            r2.setUser(u2);
            r2.setStatus("approved");
            r2.setCreatedAt(LocalDateTime.now().minusDays(2));
            registrationRepository.save(r2);

            Feedback f1 = new Feedback();
            f1.setActivity(a);
            f1.setUser(u1);
            f1.setRating(5);
            f1.setContent("很好，联系 13800138000");
            f1.setCreatedAt(LocalDateTime.now());
            feedbackRepository.save(f1);

            Feedback f2 = new Feedback();
            f2.setActivity(a);
            f2.setUser(u2);
            f2.setRating(4);
            f2.setContent("整体不错");
            f2.setCreatedAt(LocalDateTime.now());
            feedbackRepository.save(f2);
        });
        Cache c = cacheManager.getCache(CacheNames.ANALYTICS_ACTIVITY);
        if (c != null) {
            c.evict(activityId);
        }
        Cache fb = cacheManager.getCache(CacheNames.FEEDBACK_BY_ACTIVITY);
        if (fb != null) {
            fb.evict(activityId);
        }
    }
}
