package com.example.campusactivity.controller;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRun;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.UserRepository;
import com.example.campusactivity.service.clustering.ClusteringRunFailureCode;
import com.example.campusactivity.service.clustering.CommunityClusteringOrchestrator;
import com.example.campusactivity.service.clustering.CommunityClusteringQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "campus.demo-password=TestPassword123!",
        "community-clustering.python.enabled=false"
})
@AutoConfigureMockMvc
class AdminCommunityClusteringDisabledIntegrationTest {
    private static final String PASSWORD = "StrongPassword123!";
    private static final String ADMIN_ID = "clustering-disabled-admin";
    private static final String SUCCESS_ID = "disabled-success-run";
    private static final String FAILED_ID = "disabled-failed-run";
    private static final Instant CREATED_AT =
            Instant.parse("2026-07-20T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClusteringRunRepository runRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private Environment environment;
    @Autowired
    private AdminCommunityClusteringController controller;
    @Autowired
    private CommunityClusteringQueryService queryService;

    @BeforeEach
    void createFixtures() {
        deleteFixtures();
        userRepository.save(admin());
        runRepository.saveAndFlush(successfulRun());
        runRepository.saveAndFlush(failedRun());
    }

    @AfterEach
    void deleteFixtures() {
        runRepository.deleteAllById(List.of(SUCCESS_ID, FAILED_ID));
        userRepository.deleteById(ADMIN_ID);
    }

    @Test
    void disabledPythonKeepsControllerAndHistoricalRunQueriesAvailable()
            throws Exception {
        assertThat(environment.getProperty(
                "community-clustering.python.enabled",
                Boolean.class
        )).isFalse();
        assertThat(controller).isNotNull();
        assertThat(queryService).isNotNull();
        assertThat(applicationContext.getBeansOfType(
                CommunityClusteringOrchestrator.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(
                ClusteringClient.class)).isEmpty();

        MockHttpSession admin = loggedIn();
        mockMvc.perform(get(
                        "/api/v1/admin/community-clustering/runs/{runId}",
                        SUCCESS_ID
                ).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.sampleCount").value(3))
                .andExpect(jsonPath("$.metrics.inertia").value(2.5))
                .andExpect(jsonPath(
                        "$.metrics.pcaExplainedVarianceRatio[0]"
                ).value(0.65))
                .andExpect(jsonPath(
                        "$.metrics.pcaExplainedVarianceRatio[1]"
                ).value(0.25))
                .andExpect(jsonPath("$.failure").value((Object) null))
                .andExpect(jsonPath("$.createdBy").value(ADMIN_ID))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/community-clustering/runs")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].runId").value(SUCCESS_ID))
                .andExpect(jsonPath("$.items[1].runId").value(FAILED_ID));

        mockMvc.perform(get(
                        "/api/v1/admin/community-clustering/runs/{runId}",
                        FAILED_ID
                ).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.metrics").value((Object) null))
                .andExpect(jsonPath("$.failure.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.failure.message").value(
                        "INTERNAL_ERROR: 聚类运行发生内部错误"
                ))
                .andExpect(jsonPath("$.startedAt").value((Object) null))
                .andExpect(jsonPath("$.finishedAt").isString())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    private MockHttpSession loggedIn() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session =
                (MockHttpSession) csrfResult.getRequest().getSession(false);
        JsonNode csrf = objectMapper.readTree(
                csrfResult.getResponse().getContentAsString(
                        StandardCharsets.UTF_8
                )
        );
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(
                                csrf.get("headerName").asText(),
                                csrf.get("token").asText()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", ADMIN_ID,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk());
        return session;
    }

    private UserAccount admin() {
        UserAccount user = new UserAccount();
        user.setId(ADMIN_ID);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setName("Clustering Disabled Admin");
        user.setRole("admin");
        user.setCollege("Software");
        user.setGrade("2026");
        user.setInterests(new ArrayList<>(List.of("AI")));
        user.setAvailableTime(new ArrayList<>(List.of("weekend")));
        user.setFriends(new ArrayList<>());
        return user;
    }

    private static ClusteringRun successfulRun() {
        ClusteringRun run = baseRun(
                SUCCESS_ID,
                "disabled-success-version",
                ClusteringRunStatus.SUCCESS
        );
        run.setSampleCount(3);
        run.setStartedAt(CREATED_AT.plusSeconds(1));
        run.setFinishedAt(CREATED_AT.plusSeconds(2));
        run.setMetricsJson(
                "{\"inertia\":2.5,\"pcaExplainedVarianceRatio\":[0.65,0.25]}"
        );
        return run;
    }

    private static ClusteringRun failedRun() {
        ClusteringRun run = baseRun(
                FAILED_ID,
                "disabled-failed-version",
                ClusteringRunStatus.FAILED
        );
        run.setFinishedAt(CREATED_AT.plusSeconds(3));
        run.setErrorMessage(
                ClusteringRunFailureCode.INTERNAL_ERROR.errorMessage()
        );
        return run;
    }

    private static ClusteringRun baseRun(
            String id,
            String version,
            ClusteringRunStatus status
    ) {
        ClusteringRun run = new ClusteringRun();
        run.setId(id);
        run.setVersion(version);
        run.setAlgorithm(ClusteringAlgorithm.KMEANS);
        run.setClusterCount(2);
        run.setRandomState(42);
        run.setStatus(status);
        run.setActiveSlot(null);
        run.setFeatureSchemaVersion("community-features-v1");
        run.setParametersJson("{}");
        run.setCreatedBy(ADMIN_ID);
        run.setCreatedAt(CREATED_AT);
        return run;
    }
}
