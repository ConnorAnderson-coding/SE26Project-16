package com.example.demo.controller;

import com.example.demo.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest extends IntegrationTestSupport {

    @Test
    void protectedEndpointWithoutTokenShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointWithInvalidTokenShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointWithValidTokenShouldReturn200() throws Exception {
        TestScenario scenario = createScenario();

        authGet(scenario.studentToken(), "/api/v1/users/me")
                .andExpect(status().isOk());
    }
}
