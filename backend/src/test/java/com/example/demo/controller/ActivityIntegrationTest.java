package com.example.demo.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.support.IntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
class ActivityIntegrationTest extends IntegrationTestSupport {

    private TestScenario scenario;

    @BeforeEach
    public void setUp() {
        scenario = createScenario();
    }

    @Test
    void listActivitiesShouldReturnPagedResult() throws Exception {
        authGet(scenario.studentToken(), "/api/v1/activities?page=0&size=10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    @Test
    void getActivityByIdShouldReturnDetail() throws Exception {
        authGet(scenario.studentToken(), "/api/v1/activities/" + scenario.activity().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(scenario.activity().getId()))
                .andExpect(jsonPath("$.data.organizerName").value(scenario.organizer().getName()));
    }

    @Test
    void createActivityShouldSucceedForAuthenticatedUser() throws Exception {
        authPost(scenario.organizerToken(), "/api/v1/activities", buildActivityRequest("新创建活动"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("新创建活动"))
                .andExpect(jsonPath("$.data.status").value("published"));
    }

    @Test
    void updateActivityShouldSucceedForOrganizer() throws Exception {
        authPut(scenario.organizerToken(), "/api/v1/activities/" + scenario.activity().getId(),
                        buildActivityRequest("组织者更新标题"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("组织者更新标题"));
    }

    @Test
    void updateActivityShouldFailForNonOrganizer() throws Exception {
        authPut(scenario.studentToken(), "/api/v1/activities/" + scenario.activity().getId(),
                        buildActivityRequest("非法更新"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void deleteActivityShouldSucceedForOrganizer() throws Exception {
        authDelete(scenario.organizerToken(), "/api/v1/activities/" + scenario.activity().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void getRecommendedShouldReturnActivities() throws Exception {
        authGet(scenario.studentToken(), "/api/v1/activities/recommended?limit=3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getMineShouldReturnOrganizerActivities() throws Exception {
        authGet(scenario.organizerToken(), "/api/v1/activities/mine")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].title").value(scenario.activity().getTitle()));
    }
}
