package com.example.demo.controller;

import com.example.demo.dto.request.UpdateProfileRequest;
import com.example.demo.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserIntegrationTest extends IntegrationTestSupport {

    private TestScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = createScenario();
    }

    @Test
    void getMeShouldReturnCurrentUser() throws Exception {
        authGet(scenario.studentToken(), "/api/v1/users/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(scenario.student().getId()))
                .andExpect(jsonPath("$.data.name").value(scenario.student().getName()));
    }

    @Test
    void updateProfileShouldReturnUpdatedUserAndEvictCache() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setName("更新后的姓名");
        request.setCollege("软件学院");
        request.setGrade("2025");
        request.setInterests(List.of("AI"));

        authPut(scenario.studentToken(), "/api/v1/users/me", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("更新后的姓名"))
                .andExpect(jsonPath("$.data.college").value("软件学院"));

        authGet(scenario.studentToken(), "/api/v1/users/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("更新后的姓名"));
    }
}
