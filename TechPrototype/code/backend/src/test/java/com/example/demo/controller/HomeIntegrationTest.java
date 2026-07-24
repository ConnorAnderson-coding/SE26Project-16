package com.example.demo.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.dto.request.RegistrationRequest;
import com.example.demo.support.IntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
class HomeIntegrationTest extends IntegrationTestSupport {

    private TestScenario scenario;

    @BeforeEach
    public void setUp() {
        scenario = createScenario();
    }

    @Test
    void statsShouldReturnUserCounts() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setActivityId(scenario.activity().getId());
        authPost(scenario.studentToken(), "/api/v1/registrations", request)
                .andExpect(status().isOk());

        authPost(scenario.studentToken(), "/api/v1/favorites/" + scenario.activity().getId(), null)
                .andExpect(status().isOk());

        authGet(scenario.studentToken(), "/api/v1/home/stats")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mySignupCount").value(1))
                .andExpect(jsonPath("$.data.myFavoriteCount").value(1));
    }
}
