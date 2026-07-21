package com.example.campusactivity.controller;

import com.example.campusactivity.client.clustering.ClusteringClient;
import com.example.campusactivity.client.clustering.dto.ClusteringMetrics;
import com.example.campusactivity.client.clustering.dto.ClusteringRequest;
import com.example.campusactivity.client.clustering.dto.ClusteringResponse;
import com.example.campusactivity.client.clustering.dto.CommunitySummary;
import com.example.campusactivity.client.clustering.dto.FeatureSample;
import com.example.campusactivity.client.clustering.dto.MemberResult;
import com.example.campusactivity.client.clustering.exception.ClusteringServiceUnavailableException;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.ClusteringRunInputRepository;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.UserRepository;
import com.example.campusactivity.service.clustering.CommunityFeatureAggregationService;
import com.example.campusactivity.service.clustering.FeatureAggregationDiagnostics;
import com.example.campusactivity.service.clustering.FeatureAggregationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "community-clustering.python.enabled=true",
        "community-clustering.python.base-url=http://localhost:8000",
        "community-clustering.dispatcher.poll-interval-ms=10",
        "community-clustering.dispatcher.initial-delay-ms=10"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminCommunityClusteringAsyncIntegrationTest {
    private static final String PATH = "/api/v1/admin/community-clustering/runs";
    private static final String PASSWORD = "StrongPassword123!";
    private static final String ADMIN_ID = "async-post-admin";
    private static final List<String> IDS = List.of(
            ADMIN_ID,
            "async-post-student-1",
            "async-post-student-2"
    );

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ClusteringRunRepository runRepository;
    @Autowired
    private ClusteringRunInputRepository inputRepository;
    @MockBean
    private CommunityFeatureAggregationService aggregationService;
    @MockBean
    private ClusteringClient clusteringClient;

    private CountDownLatch pythonStarted;
    private CountDownLatch releasePython;
    private AtomicInteger pythonCalls;

    @BeforeEach
    void setUp() {
        deleteFixtures();
        userRepository.save(user(ADMIN_ID, "admin"));
        userRepository.save(user(IDS.get(1), "student"));
        userRepository.save(user(IDS.get(2), "student"));
        when(aggregationService.aggregateFeatureSamples()).thenReturn(aggregation());
        pythonStarted = new CountDownLatch(1);
        releasePython = new CountDownLatch(1);
        pythonCalls = new AtomicInteger();
        when(clusteringClient.runClustering(any())).thenAnswer(invocation -> {
            pythonCalls.incrementAndGet();
            pythonStarted.countDown();
            if (!releasePython.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("test did not release Python substitute");
            }
            throw new ClusteringServiceUnavailableException();
        });
    }

    @AfterEach
    void deleteFixtures() {
        if (releasePython != null) {
            releasePython.countDown();
        }
        inputRepository.deleteAll();
        runRepository.deleteAll();
        userRepository.deleteAllById(IDS);
    }

    @Test
    void acceptedResponseReturnsWhilePythonIsBlockedAndConcurrentPostConflicts()
            throws Exception {
        SessionCsrf admin = loggedInWithFreshCsrf(ADMIN_ID);

        MvcResult accepted = mockMvc.perform(post(PATH)
                        .session(admin.session())
                        .header(admin.headerName(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.clusterCount").value(2))
                .andReturn();

        String runId = json(accepted).get("runId").asText();
        assertThat(accepted.getResponse().getHeader("Location"))
                .isEqualTo(PATH + "/" + runId);
        assertThat(pythonStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(runRepository.findById(runId)).isPresent();
        assertThat(inputRepository.countByRunId(runId)).isEqualTo(2);
        assertThat(runRepository.findById(runId).orElseThrow().getCreatedBy())
                .isEqualTo(ADMIN_ID);

        mockMvc.perform(post(PATH)
                        .session(admin.session())
                        .header(admin.headerName(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RUN_CONFLICT"));
        assertThat(runRepository.count()).isEqualTo(1);
        assertThat(inputRepository.count()).isEqualTo(2);
        assertThat(pythonCalls).hasValue(1);

        releasePython.countDown();
        waitForStatus(runId, ClusteringRunStatus.FAILED, Duration.ofSeconds(5));

        MvcResult secondAccepted = mockMvc.perform(post(PATH)
                        .session(admin.session())
                        .header(admin.headerName(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clusterCount\":2}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        assertThat(runRepository.count()).isEqualTo(2);
        waitForStatus(
                json(secondAccepted).get("runId").asText(),
                ClusteringRunStatus.FAILED,
                Duration.ofSeconds(5)
        );
        verify(aggregationService, org.mockito.Mockito.times(3))
                .aggregateFeatureSamples();
    }

    @Test
    void acceptedRunCompletesInBackgroundAndFeedsDetailLatestAndMe()
            throws Exception {
        doAnswer(invocation -> {
            pythonCalls.incrementAndGet();
            pythonStarted.countDown();
            if (!releasePython.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("test did not release Python substitute");
            }
            return successfulResponse(invocation.getArgument(0));
        }).when(clusteringClient).runClustering(any());
        SessionCsrf admin = loggedInWithFreshCsrf(ADMIN_ID);

        MvcResult accepted = mockMvc.perform(post(PATH)
                        .session(admin.session())
                        .header(admin.headerName(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String runId = json(accepted).get("runId").asText();

        assertThat(pythonStarted.await(5, TimeUnit.SECONDS)).isTrue();
        mockMvc.perform(get(PATH + "/" + runId).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.createdBy").value(ADMIN_ID));

        releasePython.countDown();
        waitForStatus(runId, ClusteringRunStatus.SUCCESS, Duration.ofSeconds(5));
        mockMvc.perform(get(PATH + "/" + runId).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.sampleCount").value(2))
                .andExpect(jsonPath("$.metrics.inertia").value(1.0));

        SessionCsrf student = loggedInWithFreshCsrf(IDS.get(1));
        mockMvc.perform(get("/api/v1/community-clustering/latest")
                        .session(student.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.runId").value(runId))
                .andExpect(jsonPath("$.communities.length()").value(2));
        mockMvc.perform(get("/api/v1/community-clustering/me")
                        .session(student.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.membership.clusterNo").isNumber())
                .andExpect(jsonPath("$.membership.x").isNumber());

        assertThat(pythonCalls).hasValue(1);
    }

    private void waitForStatus(
            String runId,
            ClusteringRunStatus expected,
            Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (runRepository.findById(runId)
                    .map(run -> run.getStatus() == expected)
                    .orElse(false)) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(runRepository.findById(runId).orElseThrow().getStatus())
                .isEqualTo(expected);
    }

    private SessionCsrf loggedInWithFreshCsrf(String userId) throws Exception {
        MvcResult initial = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) initial.getRequest().getSession(false);
        JsonNode token = json(initial);
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", userId,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk());
        MvcResult refreshed = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode fresh = json(refreshed);
        return new SessionCsrf(
                session,
                fresh.get("headerName").asText(),
                fresh.get("token").asText()
        );
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(
                StandardCharsets.UTF_8
        ));
    }

    private UserAccount user(String id, String role) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setName("Async Post User");
        user.setRole(role);
        return user;
    }

    private static FeatureAggregationResult aggregation() {
        return new FeatureAggregationResult(
                List.of(sample(IDS.get(1)), sample(IDS.get(2))),
                List.of(),
                new FeatureAggregationDiagnostics(0, 0, 0, 0, 0, 0, 0, 0)
        );
    }

    private static FeatureSample sample(String userId) {
        return new FeatureSample(
                userId,
                List.of("AI"),
                "Computer Science",
                "2026",
                List.of("MONDAY"),
                1,
                1,
                0,
                0,
                0,
                null,
                Map.of("Technology", 1)
        );
    }

    private static ClusteringResponse successfulResponse(ClusteringRequest request) {
        List<FeatureSample> samples = request.samples();
        return new ClusteringResponse(
                request.runId(),
                request.version(),
                request.algorithm(),
                request.clusterCount(),
                samples.size(),
                new ClusteringMetrics(1.0, List.of(0.75, 0.25)),
                List.of(
                        new CommunitySummary(0, 1, List.of("AI")),
                        new CommunitySummary(1, 1, List.of("AI"))
                ),
                List.of(
                        new MemberResult(samples.get(0).userId(), 0, 10.0, 20.0, 0.1),
                        new MemberResult(samples.get(1).userId(), 1, 90.0, 80.0, 0.2)
                )
        );
    }

    private record SessionCsrf(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }
}
