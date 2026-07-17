package com.example.demo.controller;

import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends IntegrationTestSupport {

    @Test
    void loginWithValidCredentialsShouldReturnToken() throws Exception {
        TestScenario scenario = createScenario();

        LoginRequest request = new LoginRequest();
        request.setUserId(scenario.student().getId());
        request.setPassword(PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").value(scenario.student().getId()));
    }

    @Test
    void loginWithDifferentIdCasingShouldReturnCanonicalUserId() throws Exception {
        String canonicalId = "CaseTeacher" + System.currentTimeMillis();
        transactionTemplate.executeWithoutResult(status -> saveUser(canonicalId, "teacher", "Case Teacher"));

        LoginRequest request = new LoginRequest();
        request.setUserId(canonicalId.toLowerCase());
        request.setPassword(PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.id").value(canonicalId));
    }

    @Test
    void loginWithWrongPasswordShouldReturn401() throws Exception {
        TestScenario scenario = createScenario();

        LoginRequest request = new LoginRequest();
        request.setUserId(scenario.student().getId());
        request.setPassword("wrong-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void registerShouldCreateUserAndReturnToken() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setId("new-user-" + System.currentTimeMillis());
        request.setPassword(PASSWORD);
        request.setName("新用户");
        request.setCollege("计算机学院");
        request.setGrade("2025");
        request.setInterests(List.of("音乐"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.name").value("新用户"));
    }

    @Test
    void registerDuplicateUserShouldFail() throws Exception {
        TestScenario scenario = createScenario();

        RegisterRequest request = new RegisterRequest();
        request.setId(scenario.student().getId());
        request.setPassword(PASSWORD);
        request.setName("重复用户");
        request.setCollege("计算机学院");
        request.setGrade("2024");

        var response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        var json = objectMapper.readTree(response.getResponse().getContentAsString());
        assertFalse(json.path("code").asInt() == 0);
        assertEquals("该学号/工号已注册", json.path("message").asText());
    }
}
