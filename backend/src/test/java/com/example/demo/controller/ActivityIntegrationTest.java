package com.example.demo.controller;

import com.example.demo.support.IntegrationTestSupport;
import com.example.demo.repository.ActivityViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
class ActivityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ActivityViewRepository activityViewRepository;

    private TestScenario scenario;

    @BeforeEach
    void setUp() {
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
    void viewsShouldCountEveryRoleOnceAcrossTheWholeActivityLifecycle() throws Exception {
        scenario.activity().setStatus("ended");
        activityRepository.saveAndFlush(scenario.activity());
        long activityId = scenario.activity().getId();

        authGet(scenario.studentToken(), "/api/v1/activities/" + activityId)
                .andExpect(status().isOk());
        assertEquals(1, activityViewRepository.countByActivityId(activityId));
        assertEquals(1, activityRepository.findById(activityId).orElseThrow().getViewCount());

        authGet(scenario.studentToken(), "/api/v1/activities/" + activityId)
                .andExpect(status().isOk());
        assertEquals(1, activityViewRepository.countByActivityId(activityId));
        assertEquals(1, activityRepository.findById(activityId).orElseThrow().getViewCount());

        authGet(scenario.organizerToken(), "/api/v1/activities/" + activityId)
                .andExpect(status().isOk());
        authGet(scenario.adminToken(), "/api/v1/activities/" + activityId)
                .andExpect(status().isOk());

        assertEquals(3, activityViewRepository.countByActivityId(activityId));
        assertEquals(3, activityRepository.findById(activityId).orElseThrow().getViewCount());
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
