package com.example.demo.controller;

import com.example.demo.dto.request.ActivityRecordRequest;
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
class ActivityRecordIntegrationTest extends IntegrationTestSupport {

    private TestScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = createScenario();
    }

    @Test
    void publishRecordShouldSucceedForOrganizer() throws Exception {
        ActivityRecordRequest request = new ActivityRecordRequest();
        request.setSummary("活动圆满结束");
        request.setPhotos(List.of("photo1.jpg"));

        authPost(scenario.organizerToken(),
                        "/api/v1/activities/" + scenario.activity().getId() + "/record", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.summary").value("活动圆满结束"));

        authGet(scenario.studentToken(), "/api/v1/activities/" + scenario.activity().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ended"))
                .andExpect(jsonPath("$.data.record.summary").value("活动圆满结束"));
    }

    @Test
    void publishRecordShouldFailForNonOrganizer() throws Exception {
        ActivityRecordRequest request = new ActivityRecordRequest();
        request.setSummary("非法发布");

        authPost(scenario.studentToken(),
                        "/api/v1/activities/" + scenario.activity().getId() + "/record", request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));
    }
}
