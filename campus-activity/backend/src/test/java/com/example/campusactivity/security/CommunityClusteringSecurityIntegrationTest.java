package com.example.campusactivity.security;

import com.example.campusactivity.dto.clustering.ClusteringRunDetailResponse;
import com.example.campusactivity.dto.clustering.ClusteringRunSummaryResponse;
import com.example.campusactivity.dto.clustering.CurrentUserClusteringResponse;
import com.example.campusactivity.dto.clustering.LatestClusteringResponse;
import com.example.campusactivity.entity.ClusteringAlgorithm;
import com.example.campusactivity.entity.ClusteringRunStatus;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.UserRepository;
import com.example.campusactivity.service.clustering.CommunityClusteringQueryService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "campus.demo-password=TestPassword123!",
        "community-clustering.python.enabled=false"
})
@AutoConfigureMockMvc
class CommunityClusteringSecurityIntegrationTest {
    private static final String PASSWORD = "StrongPassword123!";
    private static final String STUDENT_ID = "user-query-security-student";
    private static final String TEACHER_ID = "user-query-security-teacher";
    private static final String ADMIN_ID = "user-query-security-admin";
    private static final String OTHER_ID = "forged-other-user";
    private static final String RUN_ID = "user-query-security-run";
    private static final String LATEST_PATH =
            "/api/v1/community-clustering/latest";
    private static final String ME_PATH = "/api/v1/community-clustering/me";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockBean
    private CommunityClusteringQueryService queryService;

    @BeforeEach
    void createUsersAndResponses() {
        deleteUsers();
        userRepository.save(user(STUDENT_ID, "student"));
        userRepository.save(user(TEACHER_ID, "teacher"));
        userRepository.save(user(ADMIN_ID, "admin"));
        when(queryService.findLatestClustering(anyString()))
                .thenAnswer(invocation -> latestResponse());
        when(queryService.findCurrentUserClustering(anyString()))
                .thenAnswer(invocation -> currentUserResponse());
        when(queryService.findRunById(RUN_ID)).thenReturn(runResponse());
    }

    @AfterEach
    void deleteUsers() {
        userRepository.deleteAllById(List.of(
                STUDENT_ID,
                TEACHER_ID,
                ADMIN_ID
        ));
    }

    @Test
    void anonymousCannotReachEitherEndpointWithForgedIdentity() throws Exception {
        mockMvc.perform(get(LATEST_PATH)
                        .header("X-Role", "admin")
                        .header("X-User-Id", OTHER_ID)
                        .queryParam("userId", OTHER_ID))
                .andExpect(fixedError(
                        401,
                        "AUTHENTICATION_REQUIRED",
                        "请先登录"
                ));
        mockMvc.perform(get(ME_PATH)
                        .header("X-Role", "admin")
                        .header("X-User-Id", OTHER_ID)
                        .queryParam("userId", OTHER_ID))
                .andExpect(fixedError(
                        401,
                        "AUTHENTICATION_REQUIRED",
                        "请先登录"
                ));

        verifyNoInteractions(queryService);
    }

    @Test
    void studentTeacherAndAdminCanQueryBothEndpointsWithoutCsrf()
            throws Exception {
        for (String accountId : List.of(STUDENT_ID, TEACHER_ID, ADMIN_ID)) {
            MockHttpSession session = loggedIn(accountId);

            mockMvc.perform(get(LATEST_PATH).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.run.runId").value(RUN_ID));
            mockMvc.perform(get(ME_PATH).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.runId").value(RUN_ID));

            verify(queryService).findLatestClustering(accountId);
            verify(queryService).findCurrentUserClustering(accountId);
        }
    }

    @Test
    void headersAndExtraQueryUserIdCannotChangeSessionIdentity()
            throws Exception {
        MockHttpSession session = loggedIn(STUDENT_ID);

        mockMvc.perform(get(LATEST_PATH)
                        .session(session)
                        .header("X-Role", "admin")
                        .header("X-User-Id", OTHER_ID)
                        .queryParam("userId", OTHER_ID))
                .andExpect(status().isOk());
        mockMvc.perform(get(ME_PATH)
                        .session(session)
                        .header("X-Role", "admin")
                        .header("X-User-Id", OTHER_ID)
                        .queryParam("userId", OTHER_ID))
                .andExpect(status().isOk());

        verify(queryService).findLatestClustering(STUDENT_ID);
        verify(queryService).findCurrentUserClustering(STUDENT_ID);
    }

    @Test
    void existingAdminRunAndUsersRulesRemainUnchanged() throws Exception {
        MockHttpSession student = loggedIn(STUDENT_ID);
        MockHttpSession admin = loggedIn(ADMIN_ID);
        String runPath = "/api/v1/admin/community-clustering/runs/" + RUN_ID;

        mockMvc.perform(get(runPath).session(student))
                .andExpect(fixedError(403, "ACCESS_DENIED", "无权访问该资源"));
        mockMvc.perform(get(runPath).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(RUN_ID));

        mockMvc.perform(get("/api/users").session(student))
                .andExpect(fixedError(403, "ACCESS_DENIED", "无权访问该资源"));
        mockMvc.perform(get("/api/users").session(admin))
                .andExpect(status().isOk());

        verify(queryService).findRunById(RUN_ID);
    }

    private MockHttpSession loggedIn(String id) throws Exception {
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
                                "id", id,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk());
        return session;
    }

    private UserAccount user(String id, String role) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setName("Community Query Security User");
        user.setRole(role);
        user.setCollege("Software");
        user.setGrade("2026");
        user.setInterests(new ArrayList<>(List.of("AI")));
        user.setAvailableTime(new ArrayList<>(List.of("weekend")));
        user.setFriends(new ArrayList<>());
        return user;
    }

    private static LatestClusteringResponse latestResponse() {
        return new LatestClusteringResponse(
                new ClusteringRunSummaryResponse(
                        RUN_ID,
                        "security-version",
                        ClusteringAlgorithm.KMEANS,
                        2,
                        2,
                        Instant.parse("2026-07-21T03:00:00Z")
                ),
                List.of()
        );
    }

    private static CurrentUserClusteringResponse currentUserResponse() {
        return new CurrentUserClusteringResponse(
                RUN_ID,
                "security-version",
                null
        );
    }

    private static ClusteringRunDetailResponse runResponse() {
        return new ClusteringRunDetailResponse(
                RUN_ID,
                "security-version",
                ClusteringAlgorithm.KMEANS,
                2,
                42,
                ClusteringRunStatus.PENDING,
                null,
                "community-features-v1",
                null,
                null,
                Instant.parse("2026-07-21T03:00:00Z"),
                null,
                null,
                ADMIN_ID
        );
    }

    private static org.springframework.test.web.servlet.ResultMatcher fixedError(
            int expectedStatus,
            String expectedCode,
            String expectedMessage
    ) {
        return result -> {
            assertThat(result.getResponse().getStatus())
                    .isEqualTo(expectedStatus);
            assertThat(MediaType.parseMediaType(
                    result.getResponse().getContentType()
            ).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
            JsonNode json = new ObjectMapper().readTree(
                    result.getResponse().getContentAsString(
                            StandardCharsets.UTF_8
                    )
            );
            assertThat(json.fieldNames()).toIterable()
                    .containsExactlyInAnyOrder("code", "message", "details");
            assertThat(json.get("code").asText()).isEqualTo(expectedCode);
            assertThat(json.get("message").asText()).isEqualTo(expectedMessage);
            assertThat(json.get("details").isObject()).isTrue();
            assertThat(json.get("details").isEmpty()).isTrue();
        };
    }
}
