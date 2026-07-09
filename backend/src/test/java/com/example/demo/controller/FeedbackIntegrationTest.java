package com.example.demo.controller;

import com.example.demo.dto.request.FeedbackRequest;
import com.example.demo.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FeedbackIntegrationTest extends IntegrationTestSupport {

    private TestScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = createScenario();
    }

    @Test
    void submitFeedbackShouldSucceed() throws Exception {
        FeedbackRequest request = new FeedbackRequest();
        request.setActivityId(scenario.activity().getId());
        request.setRating(5);
        request.setContent("活动很棒");

        authPost(scenario.studentToken(), "/api/v1/feedbacks", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.content").value("活动很棒"));
    }

    @Test
    void listFeedbacksByActivityShouldReturnSubmittedFeedback() throws Exception {
        FeedbackRequest request = new FeedbackRequest();
        request.setActivityId(scenario.activity().getId());
        request.setRating(4);
        request.setContent("体验不错");
        authPost(scenario.studentToken(), "/api/v1/feedbacks", request)
                .andExpect(status().isOk());

        authGet(scenario.studentToken(), "/api/v1/feedbacks?activityId=" + scenario.activity().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("体验不错"));
    }

    @Test
    void getMineShouldReturnUserFeedbacks() throws Exception {
        FeedbackRequest request = new FeedbackRequest();
        request.setActivityId(scenario.activity().getId());
        request.setRating(3);
        request.setContent("我的反馈");
        authPost(scenario.studentToken(), "/api/v1/feedbacks", request)
                .andExpect(status().isOk());

        authGet(scenario.studentToken(), "/api/v1/feedbacks/mine")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("我的反馈"));
    }
}
