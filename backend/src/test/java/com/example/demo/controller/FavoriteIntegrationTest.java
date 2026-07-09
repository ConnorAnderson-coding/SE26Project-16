package com.example.demo.controller;

import com.example.demo.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FavoriteIntegrationTest extends IntegrationTestSupport {

    private TestScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = createScenario();
    }

    @Test
    void toggleFavoriteShouldAddAndRemove() throws Exception {
        Long activityId = scenario.activity().getId();

        authPost(scenario.studentToken(), "/api/v1/favorites/" + activityId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(true));

        authGet(scenario.studentToken(), "/api/v1/favorites/" + activityId + "/status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(true));

        authPost(scenario.studentToken(), "/api/v1/favorites/" + activityId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(false));
    }

    @Test
    void listFavoritesShouldReturnFavoritedActivities() throws Exception {
        Long activityId = scenario.activity().getId();
        authPost(scenario.studentToken(), "/api/v1/favorites/" + activityId, null)
                .andExpect(status().isOk());

        authGet(scenario.studentToken(), "/api/v1/favorites")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(activityId));
    }
}
