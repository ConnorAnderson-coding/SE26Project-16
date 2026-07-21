package com.example.campusactivity.security;

import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.ClusteringRunInputRepository;
import com.example.campusactivity.repository.ClusteringRunRepository;
import com.example.campusactivity.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "community-clustering.python.enabled=false")
@AutoConfigureMockMvc
class AdminCommunityClusteringPostSecurityIntegrationTest {
    private static final String PASSWORD = "StrongPassword123!";
    private static final String PATH = "/api/v1/admin/community-clustering/runs";
    private static final List<String> IDS = List.of(
            "post-security-student",
            "post-security-teacher",
            "post-security-admin"
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

    @BeforeEach
    void createUsers() {
        deleteFixtures();
        userRepository.save(user(IDS.get(0), "student"));
        userRepository.save(user(IDS.get(1), "teacher"));
        userRepository.save(user(IDS.get(2), "admin"));
    }

    @AfterEach
    void deleteFixtures() {
        inputRepository.deleteAll();
        runRepository.deleteAll();
        userRepository.deleteAllById(IDS);
    }

    @Test
    void anonymousAndForgedRoleCannotPost() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("X-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertNoRun();
    }

    @Test
    void studentAndTeacherAreDeniedEvenWithValidCsrf() throws Exception {
        for (int index = 0; index < 2; index++) {
            SessionCsrf authenticated = loggedInWithFreshCsrf(IDS.get(index));
            mockMvc.perform(post(PATH)
                            .session(authenticated.session())
                            .header(authenticated.headerName(), authenticated.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        assertNoRun();
    }

    @Test
    void adminWithoutCsrfIsRejectedBeforeDisabledFacade() throws Exception {
        SessionCsrf admin = loggedInWithFreshCsrf(IDS.get(2));
        mockMvc.perform(post(PATH)
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        assertNoRun();
    }

    @Test
    void disabledFacadeReturns503WithoutAggregationOrPersistence() throws Exception {
        SessionCsrf admin = loggedInWithFreshCsrf(IDS.get(2));
        mockMvc.perform(post(PATH)
                        .session(admin.session())
                        .header(admin.headerName(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("CLUSTERING_SERVICE_UNAVAILABLE"));
        assertNoRun();
    }

    @Test
    void strictParserRejectsBodyRoleForAuthenticatedAdmin() throws Exception {
        SessionCsrf admin = loggedInWithFreshCsrf(IDS.get(2));
        mockMvc.perform(post(PATH)
                        .session(admin.session())
                        .header(admin.headerName(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CLUSTERING_REQUEST"));
        assertNoRun();
    }

    private SessionCsrf loggedInWithFreshCsrf(String id) throws Exception {
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
                                "id", id,
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
        user.setName("Post Security User");
        user.setRole(role);
        return user;
    }

    private void assertNoRun() {
        assertThat(runRepository.count()).isZero();
        assertThat(inputRepository.count()).isZero();
    }

    private record SessionCsrf(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }
}
