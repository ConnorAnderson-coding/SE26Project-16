package com.example.demo.controller;

import com.example.demo.dto.request.RegistrationRequest;
import com.example.demo.dto.request.ReviewRegistrationRequest;
import com.example.demo.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationIntegrationTest extends IntegrationTestSupport {

    private TestScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = createScenario();
    }

    @Test
    void signupShouldCreateRegistration() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setActivityId(scenario.activity().getId());

        authPost(scenario.studentToken(), "/api/v1/registrations", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.activityId").value(scenario.activity().getId()));
    }

    @Test
    void duplicateSignupShouldFail() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setActivityId(scenario.activity().getId());

        authPost(scenario.studentToken(), "/api/v1/registrations", request)
                .andExpect(status().isOk());

        authPost(scenario.studentToken(), "/api/v1/registrations", request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("您已报名该活动"));
    }

    @Test
    void reviewRegistrationShouldUpdateStatus() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setActivityId(scenario.activity().getId());
        var signupResponse = parseResponse(authPost(scenario.studentToken(), "/api/v1/registrations", request));
        long registrationId = signupResponse.path("data").path("id").asLong();

        ReviewRegistrationRequest reviewRequest = new ReviewRegistrationRequest();
        reviewRequest.setApproved(true);

        authPut(scenario.organizerToken(), "/api/v1/registrations/" + registrationId + "/review", reviewRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("approved"));
    }

    @Test
    void getSignupStatusShouldReturnPendingAfterSignup() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setActivityId(scenario.activity().getId());
        authPost(scenario.studentToken(), "/api/v1/registrations", request)
                .andExpect(status().isOk());

        authGet(scenario.studentToken(), "/api/v1/registrations/status?activityId=" + scenario.activity().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("pending"));
    }

    @Test
    void getMineShouldListStudentRegistrations() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setActivityId(scenario.activity().getId());
        authPost(scenario.studentToken(), "/api/v1/registrations", request)
                .andExpect(status().isOk());

        authGet(scenario.studentToken(), "/api/v1/registrations/mine")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].activityTitle").value(scenario.activity().getTitle()));
    }
}
