package com.example.campusactivity.security;

import com.example.campusactivity.dto.clustering.ClusteringRunDetailResponse;
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
class AdminCommunityClusteringSecurityIntegrationTest {
    private static final String PASSWORD = "StrongPassword123!";
    private static final String STUDENT_ID = "clustering-security-student";
    private static final String TEACHER_ID = "clustering-security-teacher";
    private static final String ADMIN_ID = "clustering-security-admin";
    private static final String RUN_ID = "security-run";
    private static final String RUN_PATH =
            "/api/v1/admin/community-clustering/runs/" + RUN_ID;

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
    void createUsersAndResponse() {
        deleteUsers();
        userRepository.save(user(STUDENT_ID, "student"));
        userRepository.save(user(TEACHER_ID, "teacher"));
        userRepository.save(user(ADMIN_ID, "admin"));
        when(queryService.findRunById(RUN_ID)).thenReturn(response());
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
    void anonymousCannotUseHeadersOrQueryParametersToBecomeAdmin()
            throws Exception {
        mockMvc.perform(get(RUN_PATH)
                        .header("X-Role", "admin")
                        .queryParam("role", "admin"))
                .andExpect(fixedError(
                        401,
                        "AUTHENTICATION_REQUIRED",
                        "请先登录"
                ));

        verifyNoInteractions(queryService);
    }

    @Test
    void studentAndTeacherSessionsAreDeniedBeforeControllerInvocation()
            throws Exception {
        MockHttpSession student = loggedIn(STUDENT_ID);
        mockMvc.perform(get(RUN_PATH)
                        .session(student)
                        .header("X-Role", "admin"))
                .andExpect(fixedError(
                        403,
                        "ACCESS_DENIED",
                        "无权访问该资源"
                ));

        MockHttpSession teacher = loggedIn(TEACHER_ID);
        mockMvc.perform(get(RUN_PATH).session(teacher))
                .andExpect(fixedError(
                        403,
                        "ACCESS_DENIED",
                        "无权访问该资源"
                ));

        verifyNoInteractions(queryService);
    }

    @Test
    void adminSessionReachesControllerWithoutCsrfToken() throws Exception {
        MockHttpSession admin = loggedIn(ADMIN_ID);

        mockMvc.perform(get(RUN_PATH).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(RUN_ID))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(queryService).findRunById(RUN_ID);
    }

    @Test
    void existingUsersAdminRuleStillWorks() throws Exception {
        MockHttpSession student = loggedIn(STUDENT_ID);
        mockMvc.perform(get("/api/users")
                        .session(student)
                        .header("X-Role", "admin"))
                .andExpect(fixedError(
                        403,
                        "ACCESS_DENIED",
                        "无权访问该资源"
                ));

        MockHttpSession admin = loggedIn(ADMIN_ID);
        mockMvc.perform(get("/api/users").session(admin))
                .andExpect(status().isOk());
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
        user.setName("Clustering Security User");
        user.setRole(role);
        user.setCollege("Software");
        user.setGrade("2026");
        user.setInterests(new ArrayList<>(List.of("AI")));
        user.setAvailableTime(new ArrayList<>(List.of("weekend")));
        user.setFriends(new ArrayList<>());
        return user;
    }

    private static ClusteringRunDetailResponse response() {
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
                Instant.parse("2026-07-20T01:00:00Z"),
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
            String body = result.getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            JsonNode json = new ObjectMapper().readTree(body);
            assertThat(json.fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder("code", "message", "details");
            assertThat(json.get("code").asText()).isEqualTo(expectedCode);
            assertThat(json.get("message").asText()).isEqualTo(expectedMessage);
            assertThat(json.get("details").isObject()).isTrue();
            assertThat(json.get("details").isEmpty()).isTrue();
        };
    }
}
